package com.zerohour.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerohour.agents.AgentOrchestrator;
import com.zerohour.agents.NudgeAgent;
import com.zerohour.agents.PlannerAgent;
import com.zerohour.agents.PrioritizerAgent;
import com.zerohour.agents.SchedulerAgent;
import com.zerohour.models.PanicSession;
import com.zerohour.models.Subtask;
import com.zerohour.models.Task;
import com.zerohour.models.User;
import com.zerohour.services.AuthService;
import com.zerohour.services.FirestoreService;
import com.zerohour.services.GeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * PanicController — REST API for Panic Mode (F02).
 *
 * Full conversational multi-turn flow:
 *   POST   /api/panic/start             — Begin panic session, Gemini asks follow-up
 *   POST   /api/panic/{id}/reply        — Continue Q&A conversation
 *   GET    /api/panic/{id}              — Get session details (includes status + plan)
 *   GET    /api/panic/{id}/conversation — Parsed conversation history for chat UI
 *   POST   /api/panic/{id}/edit         — Replace full subtask list
 *   PUT    /api/panic/{id}/subtasks/{subtaskId} — Edit individual subtask
 *   POST   /api/panic/{id}/regenerate   — Re-trigger agent pipeline
 *   POST   /api/panic/{id}/confirm      — Confirm plan → trigger Scheduler + Nudge
 *   GET    /api/panic                   — List all user panic sessions
 *
 * Status lifecycle: QUESTIONING → GENERATING → PLAN_READY → CONFIRMED
 */
@RestController
@RequestMapping("/api/panic")
public class PanicController {

    private static final Logger log = LoggerFactory.getLogger(PanicController.class);

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    private AuthService authService;

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private PlannerAgent plannerAgent;

    @Autowired
    private PrioritizerAgent prioritizerAgent;

    @Autowired
    private SchedulerAgent schedulerAgent;

    @Autowired
    private NudgeAgent nudgeAgent;

    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── LIST ALL SESSIONS ─────────────────────────────────────────────────

    /**
     * GET /api/panic — List all panic sessions for the current user.
     * Used by Dashboard to show session history.
     */
    @GetMapping
    public ResponseEntity<List<PanicSession>> listSessions() {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        List<PanicSession> sessions = firestoreService.getPanicSessionsByUserId(userId);
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String id) {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        PanicSession session = firestoreService.getPanicSession(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        if (!session.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        firestoreService.deletePanicSession(id);
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<Map<String, String>> renameSession(
            @PathVariable String id, 
            @RequestBody Map<String, String> request) {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        PanicSession session = firestoreService.getPanicSession(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        if (!session.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String newTitle = request.get("title");
        if (newTitle == null || newTitle.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        session.setTitle(newTitle);
        firestoreService.savePanicSession(session);

        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("title", newTitle);
        return ResponseEntity.ok(response);
    }

    // ─── START PANIC SESSION ───────────────────────────────────────────────

    /**
     * POST /api/panic/start — Begin a new Panic Mode session.
     * Gemini evaluates if enough info is present; if not, asks a follow-up question.
     * When ready=true, triggers the agent pipeline via AgentOrchestrator (SSE-streamed).
     *
     * Request:  { "message": "I have an exam in 3 hours..." }
     * Response: { "sessionId", "sseSessionId", "ready", "question"|"summary", "status" }
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startPanic(@RequestBody Map<String, Object> request) {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        String userMessage = (String) request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, String> attachment = (Map<String, String>) request.get("attachment");

        String sessionId = UUID.randomUUID().toString();
        String sseSessionId = "panic-" + sessionId;

        // Build initial conversation history
        List<Map<String, String>> history = new ArrayList<>();
        Map<String, String> firstMsg = new HashMap<>();
        firstMsg.put("role", "user");
        
        String historyContent = userMessage;
        if (attachment != null && attachment.containsKey("fileName")) {
            historyContent += "\n[Attachment: " + attachment.get("fileName") + " (" + attachment.get("mimeType") + ")]";
        }
        firstMsg.put("content", historyContent);
        history.add(firstMsg);

        // Ask Gemini whether we have enough info
        Map<String, Object> geminiResponse = callPanicModel(userMessage, history, attachment);
        boolean ready = (Boolean) geminiResponse.get("ready");

        // Add assistant reply to history
        Map<String, String> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        if (ready) {
            assistantMsg.put("content", "Got it! I have enough information. Building your survival plan now...");
        } else {
            assistantMsg.put("content", (String) geminiResponse.get("question"));
        }
        history.add(assistantMsg);

        String historyJson = serializeHistory(history);
        String status = ready ? "GENERATING" : "QUESTIONING";

        PanicSession session = PanicSession.builder()
                .id(sessionId)
                .userId(userId)
                .rawInput(userMessage)
                .conversationJson(historyJson)
                .status(status)
                .sseSessionId(sseSessionId)
                .confirmed(false)
                .createdAt(new Date())
                .title(userMessage.length() > 30 ? userMessage.substring(0, 27) + "..." : userMessage)
                .build();

        if (ready) {
            session.setGeneratedPlanJson("PROCESSING");
            firestoreService.savePanicSession(session);
            // Delegate to AgentOrchestrator — streams SSE events
            agentOrchestrator.runPanicPipelineAsync(sessionId, sseSessionId);
        } else {
            firestoreService.savePanicSession(session);
        }

        Map<String, Object> responseMap = new HashMap<>(geminiResponse);
        responseMap.put("sessionId", sessionId);
        responseMap.put("sseSessionId", sseSessionId);
        responseMap.put("status", status);
        return ResponseEntity.ok(responseMap);
    }

    // ─── REPLY TO Q&A ──────────────────────────────────────────────────────

    /**
     * POST /api/panic/{id}/reply — Continue the multi-turn Q&A conversation.
     * Appends user message, calls Gemini, updates conversation history.
     * When ready=true, triggers the agent pipeline.
     *
     * Request:  { "message": "The exam covers chapters 5-8..." }
     * Response: { "sessionId", "sseSessionId", "ready", "question"|"summary", "status" }
     */
    @PostMapping("/{id}/reply")
    public ResponseEntity<Map<String, Object>> replyPanic(@PathVariable String id, @RequestBody Map<String, Object> request) {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        PanicSession session = firestoreService.getPanicSession(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        if (!session.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Don't allow replies once plan is generating/ready
        if (!"QUESTIONING".equals(session.getStatus())) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("error", "Session is no longer accepting replies. Status: " + session.getStatus());
            return ResponseEntity.badRequest().body(errorResp);
        }

        String userMessage = (String) request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, String> attachment = (Map<String, String>) request.get("attachment");

        // Rebuild conversation history
        List<Map<String, String>> history = deserializeHistory(session.getConversationJson());

        // Add user response to history
        Map<String, String> userMsgMap = new HashMap<>();
        userMsgMap.put("role", "user");
        
        String historyContent = userMessage;
        if (attachment != null && attachment.containsKey("fileName")) {
            historyContent += "\n[Attachment: " + attachment.get("fileName") + " (" + attachment.get("mimeType") + ")]";
        }
        userMsgMap.put("content", historyContent);
        history.add(userMsgMap);

        // Ask Gemini
        Map<String, Object> geminiResponse = callPanicModel(userMessage, history, attachment);
        boolean ready = (Boolean) geminiResponse.get("ready");

        // Add assistant reply to history
        Map<String, String> assistantMsgMap = new HashMap<>();
        assistantMsgMap.put("role", "assistant");
        if (ready) {
            assistantMsgMap.put("content", "Perfect! I have everything I need. Generating your rescue plan...");
        } else {
            assistantMsgMap.put("content", (String) geminiResponse.get("question"));
        }
        history.add(assistantMsgMap);

        // Update session
        session.setConversationJson(serializeHistory(history));
        String sseSessionId = session.getSseSessionId();
        if (sseSessionId == null) {
            sseSessionId = "panic-" + id;
            session.setSseSessionId(sseSessionId);
        }

        if (ready) {
            session.setStatus("GENERATING");
            session.setGeneratedPlanJson("PROCESSING");
            firestoreService.savePanicSession(session);
            // Delegate to AgentOrchestrator — streams SSE events
            agentOrchestrator.runPanicPipelineAsync(id, sseSessionId);
        } else {
            session.setStatus("QUESTIONING");
            firestoreService.savePanicSession(session);
        }

        Map<String, Object> responseMap = new HashMap<>(geminiResponse);
        responseMap.put("sessionId", id);
        responseMap.put("sseSessionId", sseSessionId);
        responseMap.put("status", session.getStatus());
        return ResponseEntity.ok(responseMap);
    }

    // ─── GET SESSION ───────────────────────────────────────────────────────

    /**
     * GET /api/panic/{id} — Retrieve full session details.
     * Includes status, generated plan JSON, confirmation state.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String id) {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        PanicSession session = firestoreService.getPanicSession(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        if (!session.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", session.getId());
        response.put("userId", session.getUserId());
        response.put("rawInput", session.getRawInput());
        response.put("status", session.getStatus());
        response.put("sseSessionId", session.getSseSessionId());
        response.put("confirmed", session.isConfirmed());
        response.put("createdAt", session.getCreatedAt());

        // Parse generated plan into structured subtask list if available
        String planJson = session.getGeneratedPlanJson();
        if (planJson != null && !planJson.isEmpty() && !"PROCESSING".equals(planJson)) {
            try {
                List<Subtask> subtasks = objectMapper.readValue(planJson, new TypeReference<List<Subtask>>() {});
                response.put("subtasks", subtasks);
                response.put("planReady", true);

                // Auto-update status if plan is ready but status is still GENERATING
                if ("GENERATING".equals(session.getStatus())) {
                    session.setStatus("PLAN_READY");
                    firestoreService.savePanicSession(session);
                    response.put("status", "PLAN_READY");
                }
            } catch (Exception e) {
                log.warn("Could not parse generatedPlanJson for session {}", id, e);
                response.put("subtasks", Collections.emptyList());
                response.put("planReady", false);
            }
        } else {
            response.put("subtasks", Collections.emptyList());
            response.put("planReady", "PROCESSING".equals(planJson));
        }

        return ResponseEntity.ok(response);
    }

    // ─── CONVERSATION HISTORY ──────────────────────────────────────────────

    /**
     * GET /api/panic/{id}/conversation — Return parsed conversation history for chat UI.
     * Response: [ { "role": "user"|"assistant", "content": "..." }, ... ]
     */
    @GetMapping("/{id}/conversation")
    public ResponseEntity<List<Map<String, String>>> getConversation(@PathVariable String id) {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        PanicSession session = firestoreService.getPanicSession(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        if (!session.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Map<String, String>> history = deserializeHistory(session.getConversationJson());
        return ResponseEntity.ok(history);
    }

    // ─── EDIT PLAN (FULL REPLACEMENT) ──────────────────────────────────────

    /**
     * POST /api/panic/{id}/edit — Replace the entire generated plan with edited subtask list.
     * Used when the user reorders, removes, or modifies multiple subtasks at once.
     */
    @PostMapping("/{id}/edit")
    public ResponseEntity<Map<String, String>> editPlan(@PathVariable String id, @RequestBody List<Subtask> subtasks) {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        PanicSession session = firestoreService.getPanicSession(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        if (!session.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Only allow edits when plan is ready
        if (!"PLAN_READY".equals(session.getStatus()) && !"GENERATING".equals(session.getStatus())) {
            Map<String, String> errorResp = new HashMap<>();
            errorResp.put("error", "Cannot edit plan in current status: " + session.getStatus());
            return ResponseEntity.badRequest().body(errorResp);
        }

        try {
            // Re-index subtasks to ensure consistent ordering
            for (int i = 0; i < subtasks.size(); i++) {
                subtasks.get(i).setOrderIndex(i);
                subtasks.get(i).setPanicSessionId(id);
            }
            session.setGeneratedPlanJson(objectMapper.writeValueAsString(subtasks));
            session.setStatus("PLAN_READY");
            firestoreService.savePanicSession(session);
        } catch (Exception e) {
            log.error("Error saving edited plan", e);
            return ResponseEntity.internalServerError().build();
        }

        Map<String, String> resp = new HashMap<>();
        resp.put("status", "ok");
        return ResponseEntity.ok(resp);
    }

    // ─── EDIT INDIVIDUAL SUBTASK ───────────────────────────────────────────

    /**
     * PUT /api/panic/{id}/subtasks/{subtaskId} — Update a single subtask within the plan.
     * Accepts partial fields: title, durationMinutes, priority, status.
     */
    @PutMapping("/{id}/subtasks/{subtaskId}")
    public ResponseEntity<Map<String, Object>> editSubtask(
            @PathVariable String id,
            @PathVariable String subtaskId,
            @RequestBody Map<String, Object> updates) {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        PanicSession session = firestoreService.getPanicSession(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        if (!session.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!"PLAN_READY".equals(session.getStatus())) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("error", "Cannot edit subtasks in current status: " + session.getStatus());
            return ResponseEntity.badRequest().body(errorResp);
        }

        try {
            List<Subtask> subtasks = objectMapper.readValue(
                    session.getGeneratedPlanJson(), new TypeReference<List<Subtask>>() {});

            boolean found = false;
            Subtask updated = null;
            for (Subtask s : subtasks) {
                if (subtaskId.equals(s.getId())) {
                    // Apply partial updates
                    if (updates.containsKey("title")) s.setTitle((String) updates.get("title"));
                    if (updates.containsKey("durationMinutes")) s.setDurationMinutes((Integer) updates.get("durationMinutes"));
                    if (updates.containsKey("priority")) s.setPriority((String) updates.get("priority"));
                    if (updates.containsKey("status")) s.setStatus((String) updates.get("status"));
                    if (updates.containsKey("orderIndex")) s.setOrderIndex((Integer) updates.get("orderIndex"));
                    found = true;
                    updated = s;
                    break;
                }
            }

            if (!found) {
                return ResponseEntity.notFound().build();
            }

            session.setGeneratedPlanJson(objectMapper.writeValueAsString(subtasks));
            firestoreService.savePanicSession(session);

            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "ok");
            resp.put("subtask", updated);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("Error editing subtask {} in session {}", subtaskId, id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ─── REGENERATE PLAN ───────────────────────────────────────────────────

    /**
     * POST /api/panic/{id}/regenerate — Re-trigger the agent pipeline.
     * Optionally accepts additional context: { "additionalContext": "..." }
     * Resets the plan and re-runs PlannerAgent → PrioritizerAgent.
     */
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<Map<String, Object>> regeneratePlan(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> request) {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        PanicSession session = firestoreService.getPanicSession(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        if (!session.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Only allow regeneration when plan is ready or was confirmed
        if (!"PLAN_READY".equals(session.getStatus()) && !"GENERATING".equals(session.getStatus())) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("error", "Cannot regenerate in current status: " + session.getStatus());
            return ResponseEntity.badRequest().body(errorResp);
        }

        // Optionally append additional context to conversation
        if (request != null && request.containsKey("additionalContext")) {
            List<Map<String, String>> history = deserializeHistory(session.getConversationJson());
            Map<String, String> ctxMsg = new HashMap<>();
            ctxMsg.put("role", "user");
            ctxMsg.put("content", "Additional context: " + request.get("additionalContext"));
            history.add(ctxMsg);
            session.setConversationJson(serializeHistory(history));
        }

        // Reset plan state
        String sseSessionId = "panic-regen-" + id + "-" + System.currentTimeMillis();
        session.setStatus("GENERATING");
        session.setGeneratedPlanJson("PROCESSING");
        session.setSseSessionId(sseSessionId);
        firestoreService.savePanicSession(session);

        // Re-trigger the orchestrator pipeline
        agentOrchestrator.runPanicPipelineAsync(id, sseSessionId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("sseSessionId", sseSessionId);
        return ResponseEntity.ok(resp);
    }

    // ─── CONFIRM PLAN ──────────────────────────────────────────────────────

    /**
     * POST /api/panic/{id}/confirm — Confirm the generated plan.
     * Creates a permanent Task + Subtasks in Firestore, then triggers
     * SchedulerAgent (→ Google Calendar) and NudgeAgent (→ reminders)
     * via AgentOrchestrator's confirmation pipeline.
     *
     * Response: { "status", "taskId", "sseSessionId" }
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<Map<String, String>> confirmPlan(@PathVariable String id) {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        PanicSession session = firestoreService.getPanicSession(id);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        if (!session.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Prevent double-confirmation
        if (session.isConfirmed() || "CONFIRMED".equals(session.getStatus())) {
            Map<String, String> errorResp = new HashMap<>();
            errorResp.put("error", "Session already confirmed");
            return ResponseEntity.badRequest().body(errorResp);
        }

        // Must have a ready plan
        if (!"PLAN_READY".equals(session.getStatus())) {
            Map<String, String> errorResp = new HashMap<>();
            errorResp.put("error", "Plan is not ready for confirmation. Status: " + session.getStatus());
            return ResponseEntity.badRequest().body(errorResp);
        }

        // Parse subtasks from generated plan
        List<Subtask> subtasks;
        try {
            subtasks = objectMapper.readValue(
                    session.getGeneratedPlanJson(), new TypeReference<List<Subtask>>() {});
        } catch (Exception e) {
            log.error("Error loading plan subtasks for confirmation", e);
            Map<String, String> errorResp = new HashMap<>();
            errorResp.put("error", "Invalid plan data");
            return ResponseEntity.badRequest().body(errorResp);
        }

        // 1. Compute total duration to set deadline
        int totalMinutes = subtasks.stream().mapToInt(Subtask::getDurationMinutes).sum();
        Date deadline = new Date(System.currentTimeMillis() + (long) totalMinutes * 60 * 1000);

        // 2. Create a permanent Task in user's Dashboard
        Task task = Task.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(session.getRawInput().length() > 60
                        ? session.getRawInput().substring(0, 57) + "..."
                        : session.getRawInput())
                .description("Generated via Panic Mode session " + session.getId())
                .status("PENDING")
                .priority("CRITICAL")
                .deadline(deadline)
                .createdAt(new Date())
                .build();

        firestoreService.saveTask(task);

        // 3. Associate subtasks with the permanent Task and save them
        for (Subtask s : subtasks) {
            s.setTaskId(task.getId());
            s.setPanicSessionId(session.getId());
            if (s.getId() == null) {
                s.setId(UUID.randomUUID().toString());
            }
        }
        firestoreService.saveSubtasks(subtasks);

        // 4. Mark panic session as confirmed
        session.setConfirmed(true);
        session.setStatus("CONFIRMED");
        firestoreService.savePanicSession(session);

        // 5. Trigger SchedulerAgent + NudgeAgent via orchestrator (SSE-streamed)
        String confirmSseSessionId = "confirm-" + task.getId();
        agentOrchestrator.runConfirmationPipelineAsync(task.getId(), userId, confirmSseSessionId);

        Map<String, String> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("taskId", task.getId());
        resp.put("sseSessionId", confirmSseSessionId);
        return ResponseEntity.ok(resp);
    }

    // ─── HELPER METHODS ────────────────────────────────────────────────────

    /**
     * Call Gemini to evaluate whether enough info has been gathered.
     * Returns { "ready": bool, "question"|"summary": string }.
     */
    private Map<String, Object> callPanicModel(String userMessage, List<Map<String, String>> conversationHistory, Map<String, String> attachment) {
        try {
            String historyStr = objectMapper.writeValueAsString(conversationHistory);

            String prompt = String.format(
                    "You are ZeroHour, an AI-powered productivity emergency assistant. A user triggered Panic Mode.\n" +
                    "Their latest message: \"%s\"\n" +
                    "Full conversation so far: %s\n\n" +
                    "Your job is to gather enough context to build a detailed rescue plan.\n" +
                    "You need to know: (1) What exactly needs to be done, (2) The deadline or time constraint,\n" +
                    "(3) Any resources, constraints, or preferences.\n\n" +
                    "Rules:\n" +
                    "- If you still need critical info, ask ONE focused follow-up question.\n" +
                    "- If you have enough info (typically after 2-3 exchanges), declare ready.\n" +
                    "- Be empathetic but efficient — the user is panicking.\n\n" +
                    "If you have enough info, respond with ONLY this JSON:\n" +
                    "{ \"ready\": true, \"summary\": \"<clear summary of goal, deadline, and context for planning>\" }\n" +
                    "Otherwise respond with ONLY this JSON:\n" +
                    "{ \"ready\": false, \"question\": \"<one focused clarifying question>\" }\n" +
                    "No markdown, no extra text — only valid JSON.",
                    userMessage,
                    historyStr
            );

            String jsonResponse;
            if (attachment != null && attachment.containsKey("mimeType") && attachment.containsKey("base64Data")) {
                jsonResponse = geminiService.generateContent(prompt, attachment.get("mimeType"), attachment.get("base64Data"));
            } else {
                jsonResponse = geminiService.generateContent(prompt);
            }
            log.debug("Panic Mode model output: {}", jsonResponse);

            JsonNode root = objectMapper.readTree(jsonResponse);
            boolean ready = root.path("ready").asBoolean(false);

            Map<String, Object> result = new HashMap<>();
            result.put("ready", ready);
            if (ready) {
                result.put("summary", root.path("summary").asText("Survival plan"));
            } else {
                result.put("question", root.path("question").asText("Can you tell me more about your deadline?"));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed in callPanicModel", e);
            // Fallback: assume ready after errors so the user isn't stuck
            Map<String, Object> result = new HashMap<>();
            result.put("ready", true);
            result.put("summary", userMessage);
            return result;
        }
    }

    /** Serialize conversation history to JSON string. */
    private String serializeHistory(List<Map<String, String>> history) {
        try {
            return objectMapper.writeValueAsString(history);
        } catch (Exception e) {
            log.error("Failed to serialize conversation history", e);
            return "[]";
        }
    }

    /** Deserialize conversation history from JSON string. */
    private List<Map<String, String>> deserializeHistory(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize conversation history", e);
            return new ArrayList<>();
        }
    }
}
