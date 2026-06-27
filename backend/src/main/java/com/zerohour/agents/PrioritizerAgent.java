package com.zerohour.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerohour.models.Subtask;
import com.zerohour.services.FirestoreService;
import com.zerohour.services.GeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PrioritizerAgent — Calls Gemini to rank subtasks by urgency × impact.
 *
 * Provides two APIs:
 * - prioritize(subtasks, taskId, sessionId)
 *   → Matches by subtask ID, saves to Firestore. Used by AgentOrchestrator pipelines.
 * - prioritize(subtasks, timeAvailableMinutes)
 *   → Matches by title, does NOT save. Backward-compatible for existing controllers.
 */
@Service
public class PrioritizerAgent {

    private static final Logger log = LoggerFactory.getLogger(PrioritizerAgent.class);

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── INNER CLASS (backward compat) ─────────────────────────────────────

    public static class RankedItem {
        public String title;
        public String priority; // CRITICAL | HIGH | MEDIUM | LOW
        public String priorityReason;
    }

    // ─── ORCHESTRATOR API ──────────────────────────────────────────────────

    /**
     * Rank subtasks by priority using Gemini.
     * Matches by subtask ID and saves updated subtasks to Firestore.
     *
     * @param subtasks  List of subtasks to rank
     * @param taskId    Parent task ID (nullable for panic sessions)
     * @param sessionId SSE session ID (for logging only)
     * @return Updated subtasks with priority fields set
     */
    public List<Subtask> prioritize(List<Subtask> subtasks, String taskId, String sessionId) {
        if (subtasks == null || subtasks.isEmpty()) return subtasks != null ? subtasks : new ArrayList<>();

        log.info("PrioritizerAgent: Prioritizing {} subtasks for task/session {}", subtasks.size(), sessionId);

        boolean needsLLM = false;
        for (Subtask s : subtasks) {
            if (s.getPriority() == null || s.getPriorityReason() == null || s.getPriorityReason().isEmpty() ||
                "Planner assigned default priority.".equals(s.getPriorityReason())) {
                needsLLM = true;
                break;
            }
        }

        if (needsLLM) {
            log.info("PrioritizerAgent: Missing custom priorities/reasons. Calling Gemini LLM...");
            // Build Gemini prompt with IDs for exact matching
            String prompt = buildPrioritizerPromptWithIds(subtasks);

            // Call Gemini
            String rawResponse = geminiService.generateContent(prompt);

            // Parse and apply priorities by ID
            applyPrioritiesFromResponseById(rawResponse, subtasks);
        } else {
            log.info("PrioritizerAgent: All subtasks already prioritized by Planner. Bypassing Gemini call for speed.");
        }

        // Save updated subtasks to Firestore
        firestoreService.saveSubtasks(subtasks);

        return subtasks;
    }

    // ─── BACKWARD-COMPATIBLE API ───────────────────────────────────────────

    /**
     * Original prioritize method — used by existing controllers.
     * Matches by title (lowercase), does NOT save to Firestore (callers handle persistence).
     */
    public List<Subtask> prioritize(List<Subtask> subtasks, int timeAvailableMinutes) {
        log.info("PrioritizerAgent: Prioritizing {} subtasks with {} minutes available.", subtasks.size(), timeAvailableMinutes);
        if (subtasks == null || subtasks.isEmpty()) {
            return subtasks;
        }

        try {
            // Convert to a simplified list of objects for Gemini context
            List<Map<String, Object>> inputList = new ArrayList<>();
            for (Subtask s : subtasks) {
                Map<String, Object> map = new HashMap<>();
                map.put("title", s.getTitle());
                map.put("durationMinutes", s.getDurationMinutes());
                inputList.add(map);
            }

            String subtasksJson = objectMapper.writeValueAsString(inputList);

            String prompt = String.format(
                    "You are a task prioritization expert. Rank these subtasks by urgency and impact.\n\n" +
                    "Subtasks: %s\n" +
                    "Total time available: %d minutes\n\n" +
                    "Return ONLY valid JSON in this format:\n" +
                    "{\n" +
                    "  \"rankedSubtasks\": [\n" +
                    "    {\n" +
                    "      \"title\": \"string (matching input title exactly)\",\n" +
                    "      \"priority\": \"CRITICAL | HIGH | MEDIUM | LOW\",\n" +
                    "      \"priorityReason\": \"string\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n" +
                    "No markdown, only JSON.",
                    subtasksJson,
                    timeAvailableMinutes
            );

            String jsonResponse = geminiService.generateContent(prompt);
            log.debug("PrioritizerAgent raw output: {}", jsonResponse);

            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode rankedArray = root.path("rankedSubtasks");

            Map<String, RankedItem> ranks = new HashMap<>();
            if (rankedArray.isArray()) {
                for (int i = 0; i < rankedArray.size(); i++) {
                    JsonNode node = rankedArray.get(i);
                    RankedItem item = new RankedItem();
                    item.title = node.path("title").asText();
                    item.priority = node.path("priority").asText("MEDIUM");
                    item.priorityReason = node.path("priorityReason").asText("");
                    ranks.put(item.title.toLowerCase().trim(), item);
                }
            }

            for (Subtask s : subtasks) {
                String key = s.getTitle().toLowerCase().trim();
                if (ranks.containsKey(key)) {
                    RankedItem ri = ranks.get(key);
                    s.setPriority(ri.priority);
                    s.setPriorityReason(ri.priorityReason);
                } else {
                    s.setPriority("MEDIUM");
                    s.setPriorityReason("Assigned default priority.");
                }
            }

        } catch (Exception e) {
            log.error("PrioritizerAgent failed to prioritize", e);
            for (Subtask s : subtasks) {
                if (s.getPriority() == null) {
                    s.setPriority("MEDIUM");
                    s.setPriorityReason("Fallback default priority.");
                }
            }
        }

        return subtasks;
    }

    // ─── SHARED HELPER ─────────────────────────────────────────────────────

    /**
     * Determine overall priority from a list of subtasks.
     * Returns the highest priority found (CRITICAL > HIGH > MEDIUM > LOW).
     */
    public String determineOverallPriority(List<Subtask> subtasks, String defaultVal) {
        if (subtasks == null || subtasks.isEmpty()) {
            return defaultVal;
        }
        boolean hasHigh = false;
        boolean hasMedium = false;
        boolean hasLow = false;

        for (Subtask s : subtasks) {
            String p = s.getPriority();
            if (p == null) continue;
            if ("CRITICAL".equalsIgnoreCase(p)) {
                return "CRITICAL";
            } else if ("HIGH".equalsIgnoreCase(p)) {
                hasHigh = true;
            } else if ("MEDIUM".equalsIgnoreCase(p)) {
                hasMedium = true;
            } else if ("LOW".equalsIgnoreCase(p)) {
                hasLow = true;
            }
        }
        if (hasHigh) return "HIGH";
        if (hasMedium) return "MEDIUM";
        if (hasLow) return "LOW";
        return defaultVal;
    }

    // ─── PROMPT (ID-based matching) ────────────────────────────────────────

    private String buildPrioritizerPromptWithIds(List<Subtask> subtasks) {
        String subtasksJson;
        try {
            subtasksJson = objectMapper.writeValueAsString(
                    subtasks.stream().map(s -> Map.of(
                            "id", s.getId() != null ? s.getId() : "",
                            "title", s.getTitle() != null ? s.getTitle() : "",
                            "durationMinutes", s.getDurationMinutes(),
                            "orderIndex", s.getOrderIndex()
                    )).toList()
            );
        } catch (Exception e) {
            subtasksJson = "[]";
        }

        return String.format(
                "You are a task prioritization expert. Rank these subtasks by urgency × impact.\n\n" +
                "Subtasks: %s\n\n" +
                "For each subtask, assign a priority level based on:\n" +
                "- CRITICAL: must be done first, blocking everything else, or highest impact\n" +
                "- HIGH: important, do before medium tasks\n" +
                "- MEDIUM: standard priority\n" +
                "- LOW: nice to have, do if time permits\n\n" +
                "Return ONLY valid JSON:\n" +
                "{\n" +
                "  \"rankings\": [\n" +
                "    {\n" +
                "      \"id\": \"string (exact subtask id)\",\n" +
                "      \"priority\": \"CRITICAL | HIGH | MEDIUM | LOW\",\n" +
                "      \"priorityReason\": \"string (one sentence explanation)\"\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "No markdown, only JSON. Include ALL subtask IDs.",
                subtasksJson
        );
    }

    // ─── RESPONSE PARSER (ID-based matching) ───────────────────────────────

    private void applyPrioritiesFromResponseById(String rawJson, List<Subtask> subtasks) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode rankings = root.get("rankings");

            // Build id → priority map
            Map<String, String> priorityMap = new HashMap<>();
            Map<String, String> reasonMap = new HashMap<>();

            if (rankings != null && rankings.isArray()) {
                for (JsonNode node : rankings) {
                    String id = node.get("id").asText();
                    String priority = node.get("priority").asText("MEDIUM");
                    String reason = node.path("priorityReason").asText("");
                    priorityMap.put(id, priority);
                    reasonMap.put(id, reason);
                }
            }

            // Apply to subtasks
            for (Subtask s : subtasks) {
                String priority = priorityMap.getOrDefault(s.getId(), "MEDIUM");
                String reason = reasonMap.getOrDefault(s.getId(), "Default priority assigned.");
                s.setPriority(priority);
                s.setPriorityReason(reason);
            }

        } catch (Exception e) {
            log.error("PrioritizerAgent: Failed to parse Gemini response, applying fallback HIGH to all.", e);
            subtasks.forEach(s -> {
                s.setPriority("HIGH");
                s.setPriorityReason("Fallback: could not parse AI priority ranking.");
            });
        }
    }
}
