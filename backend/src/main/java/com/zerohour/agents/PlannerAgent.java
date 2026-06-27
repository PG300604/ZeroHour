package com.zerohour.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerohour.models.PanicSession;
import com.zerohour.models.Subtask;
import com.zerohour.models.Task;
import com.zerohour.services.FirestoreService;
import com.zerohour.services.GeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * PlannerAgent — Calls Gemini to break a goal into subtasks.
 *
 * Day 4 hardening:
 * - Multi-attempt JSON parsing (raw → strip markdown → extract braces)
 * - Stronger JSON enforcement in prompts
 * - Richer fallback subtasks on failure
 * - Panic Mode urgency context
 */
@Service
public class PlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(PlannerAgent.class);

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── INNER CLASS (backward compat) ─────────────────────────────────────

    public static class PlanningResult {
        public List<Subtask> subtasks = new ArrayList<>();
        public int totalMinutes;
        public String feasibility; // FEASIBLE | TIGHT | RISKY
        public String agentNote;
    }

    // ─── JSON ENFORCEMENT SUFFIX ───────────────────────────────────────────

    private static final String JSON_ENFORCEMENT =
            "\n\nCRITICAL: Your entire response must be ONLY the JSON object. " +
            "Do not include any text before or after the JSON. " +
            "Do not use markdown code fences (no ```json). " +
            "Do not include explanations. " +
            "Start your response with { and end with }";

    // ─── ORCHESTRATOR API ──────────────────────────────────────────────────

    /**
     * Generate subtasks for a standard task.
     * Fetches task from Firestore, calls Gemini, parses JSON, saves subtasks.
     */
    public List<Subtask> generateSubtasks(String taskId, String sessionId) {
        Task task = firestoreService.getTask(taskId);
        if (task == null) {
            log.error("PlannerAgent: Task {} not found", taskId);
            throw new IllegalArgumentException("Task not found: " + taskId);
        }

        long minutesRemaining = calculateMinutesRemaining(task.getDeadline());
        String timeRemaining = formatTimeRemaining(minutesRemaining);

        String prompt = buildPlannerPrompt(
                task.getTitle() + ". " + (task.getDescription() != null ? task.getDescription() : ""),
                task.getDeadline() != null ? task.getDeadline().toString() : "not set",
                timeRemaining,
                ""
        );

        String rawResponse = geminiService.generateContent(prompt);
        List<Subtask> subtasks = parseSubtasksFromGeminiResponse(rawResponse, taskId, null, task.getTitle());

        firestoreService.saveSubtasks(subtasks);
        return subtasks;
    }

    /**
     * Generate subtasks from a Panic Mode session.
     */
    public List<Subtask> generateSubtasksFromPanic(String panicSessionId, String sessionId) {
        PanicSession panicSession = firestoreService.getPanicSession(panicSessionId);
        if (panicSession == null) {
            log.error("PlannerAgent: PanicSession {} not found", panicSessionId);
            return List.of(createFallbackSubtask(null, panicSessionId));
        }

        String prompt = buildPlannerPromptFromPanic(
                panicSession.getRawInput(),
                panicSession.getConversationJson()
        );

        String rawResponse = geminiService.generateContent(prompt);
        List<Subtask> subtasks = parseSubtasksFromGeminiResponse(
                rawResponse, null, panicSessionId, panicSession.getRawInput());

        firestoreService.saveSubtasks(subtasks);
        return subtasks;
    }

    // ─── BACKWARD-COMPATIBLE API ───────────────────────────────────────────

    /**
     * Original plan method — used by existing controllers (PanicController).
     * Does NOT save to Firestore (callers handle persistence).
     */
    public PlanningResult plan(String userGoal, Date deadline, String context) {
        log.info("PlannerAgent: Generating plan for goal '{}' with deadline {}", userGoal, deadline);

        Instant now = Instant.now();
        Instant deadlineInstant = deadline != null ? deadline.toInstant() : now.plus(Duration.ofHours(4));
        long minutesRemaining = Duration.between(now, deadlineInstant).toMinutes();
        String timeRemainingStr = minutesRemaining > 0 ? formatTimeRemaining(minutesRemaining) : "overdue";

        String prompt = buildPlannerPrompt(
                userGoal,
                deadline != null ? deadline.toString() : "not set",
                timeRemainingStr,
                context != null ? context : "None"
        );

        String jsonResponse = geminiService.generateContent(prompt);
        log.debug("PlannerAgent raw output: {}", jsonResponse);

        PlanningResult result = new PlanningResult();

        // Use multi-attempt parsing
        JsonNode root = parseJsonWithRetry(jsonResponse);
        if (root != null) {
            try {
                result.totalMinutes = root.path("totalMinutes").asInt();
                result.feasibility = root.path("feasibility").asText("FEASIBLE");
                result.agentNote = root.path("agentNote").asText("");

                JsonNode subtasksArray = root.path("subtasks");
                if (subtasksArray.isArray()) {
                    for (int i = 0; i < subtasksArray.size(); i++) {
                        JsonNode node = subtasksArray.get(i);
                        String priority = node.has("priority") ? node.get("priority").asText("MEDIUM") : "MEDIUM";
                        String priorityReason = node.has("priorityReason") ? node.get("priorityReason").asText("Planner assigned default priority.") : "Planner assigned default priority.";
                        Subtask subtask = Subtask.builder()
                                .title(node.path("title").asText())
                                .durationMinutes(node.path("durationMinutes").asInt(30))
                                .status("PENDING")
                                .orderIndex(node.path("orderIndex").asInt(i))
                                .priority(priority)
                                .priorityReason(priorityReason)
                                .build();
                        result.subtasks.add(subtask);
                    }
                }
            } catch (Exception e) {
                log.error("PlannerAgent: Error extracting fields from parsed JSON", e);
            }
        }

        // Fallback if parsing failed or produced no subtasks
        if (result.subtasks.isEmpty()) {
            log.warn("PlannerAgent: No subtasks parsed, using fallback plan for '{}'", userGoal);
            result.feasibility = "RISKY";
            result.agentNote = "I created a simple fallback plan because I couldn't customize the details.";
            result.subtasks.addAll(buildFallbackSubtasks(null, userGoal));
        }

        return result;
    }

    // ─── MULTI-ATTEMPT JSON PARSER (Day 4 hardening) ──────────────────────

    /**
     * Parse JSON with 3 attempts:
     * 1. Parse as-is
     * 2. Strip markdown code fences
     * 3. Extract first { } block
     */
    private JsonNode parseJsonWithRetry(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            log.error("PlannerAgent: Received null/blank response from Gemini");
            return null;
        }

        // Attempt 1: parse as-is
        try {
            return objectMapper.readTree(rawResponse);
        } catch (Exception e1) {
            log.debug("PlannerAgent: Attempt 1 (raw) failed: {}", e1.getMessage());
        }

        // Attempt 2: strip markdown code fences
        try {
            String cleaned = rawResponse
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();
            return objectMapper.readTree(cleaned);
        } catch (Exception e2) {
            log.debug("PlannerAgent: Attempt 2 (strip markdown) failed: {}", e2.getMessage());
        }

        // Attempt 3: extract first { } block
        try {
            int start = rawResponse.indexOf('{');
            int end = rawResponse.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String extracted = rawResponse.substring(start, end + 1);
                return objectMapper.readTree(extracted);
            }
        } catch (Exception e3) {
            log.debug("PlannerAgent: Attempt 3 (brace extraction) failed: {}", e3.getMessage());
        }

        log.error("PlannerAgent: JSON parse failed after 3 attempts. Raw (truncated): {}",
                rawResponse.substring(0, Math.min(200, rawResponse.length())));
        return null;
    }

    // ─── RESPONSE PARSER ───────────────────────────────────────────────────

    private List<Subtask> parseSubtasksFromGeminiResponse(String rawJson,
                                                            String taskId,
                                                            String panicSessionId,
                                                            String taskTitle) {
        JsonNode root = parseJsonWithRetry(rawJson);

        if (root != null) {
            try {
                JsonNode subtasksNode = root.get("subtasks");
                List<Subtask> result = new ArrayList<>();

                if (subtasksNode != null && subtasksNode.isArray()) {
                    for (JsonNode node : subtasksNode) {
                        String priority = node.has("priority") ? node.get("priority").asText("MEDIUM") : "MEDIUM";
                        String priorityReason = node.has("priorityReason") ? node.get("priorityReason").asText("Planner assigned default priority.") : "Planner assigned default priority.";
                        Subtask s = Subtask.builder()
                                .id(UUID.randomUUID().toString())
                                .taskId(taskId)
                                .panicSessionId(panicSessionId)
                                .title(node.get("title").asText())
                                .durationMinutes(node.get("durationMinutes").asInt(30))
                                .orderIndex(node.get("orderIndex").asInt(0))
                                .status("PENDING")
                                .priority(priority)
                                .priorityReason(priorityReason)
                                .build();
                        result.add(s);
                    }
                }

                if (!result.isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                log.error("PlannerAgent: Error extracting subtasks from parsed JSON", e);
            }
        }

        // All parsing attempts failed — use rich fallback
        log.warn("PlannerAgent: Falling back to generated subtasks for '{}'", taskTitle);
        return buildFallbackSubtasks(taskId, taskTitle != null ? taskTitle : "your task");
    }

    // ─── RICH FALLBACK (Day 4 hardening) ──────────────────────────────────

    /**
     * Build 3 sensible fallback subtasks instead of a single generic one.
     */
    private List<Subtask> buildFallbackSubtasks(String taskId, String taskTitle) {
        String safeTitle = taskTitle != null ? taskTitle : "your task";
        // Truncate very long titles for subtask naming
        if (safeTitle.length() > 50) {
            safeTitle = safeTitle.substring(0, 47) + "...";
        }

        String[] titles = {
                "Break down \"" + safeTitle + "\" into smaller steps",
                "Focus on the highest-impact part of \"" + safeTitle + "\"",
                "Review and finalize \"" + safeTitle + "\""
        };
        int[] durations = {20, 40, 15};
        String[] priorities = {"HIGH", "HIGH", "MEDIUM"};

        List<Subtask> fallbacks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Subtask s = Subtask.builder()
                    .id(UUID.randomUUID().toString())
                    .taskId(taskId)
                    .title(titles[i])
                    .durationMinutes(durations[i])
                    .orderIndex(i)
                    .status("PENDING")
                    .priority(priorities[i])
                    .priorityReason("Fallback plan — AI could not generate a custom plan.")
                    .build();
            fallbacks.add(s);
        }
        return fallbacks;
    }

    /**
     * Single fallback subtask — kept for backward compat in edge cases.
     */
    private Subtask createFallbackSubtask(String taskId, String panicSessionId) {
        return Subtask.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .panicSessionId(panicSessionId)
                .title("Work on your task")
                .durationMinutes(60)
                .orderIndex(0)
                .status("PENDING")
                .priority("HIGH")
                .priorityReason("Fallback — unable to generate custom subtasks.")
                .build();
    }

    // ─── GEMINI PROMPT TEMPLATES ───────────────────────────────────────────

    private String buildPlannerPrompt(String goal, String deadline,
                                       String timeRemaining, String context) {
        return String.format(
                "You are a productivity planning expert. A user needs a concrete action plan.\n\n" +
                "User's goal: %s\n" +
                "Deadline: %s\n" +
                "Time remaining: %s\n" +
                "Additional context: %s\n\n" +
                "Create a realistic, time-boxed action plan. Return ONLY valid JSON:\n" +
                "{\n" +
                "  \"subtasks\": [\n" +
                "    {\n" +
                "      \"title\": \"string (action-oriented, specific verb first)\",\n" +
                "      \"durationMinutes\": number,\n" +
                "      \"orderIndex\": number,\n" +
                "      \"priority\": \"CRITICAL | HIGH | MEDIUM | LOW\",\n" +
                "      \"priorityReason\": \"string (one sentence explanation of priority selection based on urgency and importance)\",\n" +
                "      \"rationale\": \"string (one sentence why this matters)\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"totalMinutes\": number,\n" +
                "  \"feasibility\": \"FEASIBLE | TIGHT | RISKY\",\n" +
                "  \"agentNote\": \"string (one sentence of honest advice to user)\"\n" +
                "}\n" +
                "Rules:\n" +
                "- Total duration must fit within time remaining\n" +
                "- First subtask always most immediately actionable\n" +
                "- No subtask longer than 45 minutes\n" +
                "- Maximum 8 subtasks\n" +
                "- No markdown, no explanation, only JSON" +
                JSON_ENFORCEMENT,
                goal, deadline, timeRemaining, context
        );
    }

    private String buildPlannerPromptFromPanic(String rawInput, String conversationJson) {
        return String.format(
                "You are ZeroHour's Planner Agent. A user triggered Panic Mode with this crisis:\n" +
                "\"%s\"\n\n" +
                "Full conversation context: %s\n\n" +
                "The user is stressed. Do not sugarcoat the plan. " +
                "Be direct, specific, and realistic about what can be achieved. " +
                "If the deadline is very tight (< 2 hours), mark the first 2 subtasks CRITICAL. " +
                "Prioritize survival over completeness.\n\n" +
                "Based on everything discussed, create a survival plan. Return ONLY valid JSON:\n" +
                "{\n" +
                "  \"subtasks\": [\n" +
                "    {\n" +
                "      \"title\": \"string\",\n" +
                "      \"durationMinutes\": number,\n" +
                "      \"orderIndex\": number,\n" +
                "      \"priority\": \"CRITICAL | HIGH | MEDIUM | LOW\",\n" +
                "      \"priorityReason\": \"string (one sentence explanation of priority selection based on urgency and importance)\",\n" +
                "      \"rationale\": \"string\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"totalMinutes\": number,\n" +
                "  \"feasibility\": \"FEASIBLE | TIGHT | RISKY\",\n" +
                "  \"agentNote\": \"string\"\n" +
                "}" +
                JSON_ENFORCEMENT,
                rawInput, conversationJson != null ? conversationJson : "[]"
        );
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────

    private long calculateMinutesRemaining(Date deadline) {
        if (deadline == null) return 240; // default 4 hours
        long deadlineMillis = deadline.getTime();
        long nowMillis = System.currentTimeMillis();
        return Math.max(0, (deadlineMillis - nowMillis) / 60_000);
    }

    private String formatTimeRemaining(long minutes) {
        if (minutes < 60) return minutes + " minutes";
        long hours = minutes / 60;
        long mins = minutes % 60;
        return hours + " hours" + (mins > 0 ? " " + mins + " minutes" : "");
    }
}
