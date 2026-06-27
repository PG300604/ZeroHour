package com.zerohour.controllers;

import com.zerohour.agents.AgentOrchestrator;
import com.zerohour.services.AuthService;
import com.zerohour.services.FirestoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * SSE endpoint — frontend connects here to receive live agent updates.
 *
 * Endpoints:
 * - GET  /api/agents/stream/{sessionId} — SSE stream of agent-update events
 * - POST /api/agents/trigger/{taskId}   — Trigger planning pipeline (for testing)
 * - POST /api/agents/confirm/{taskId}   — Trigger confirmation pipeline
 */
@RestController
@RequestMapping("/api/agents")
public class AgentStreamController {

    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Autowired
    private AuthService authService;

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    private com.zerohour.services.GeminiService geminiService;

    /**
     * GET /api/agents/test-gemini — test Gemini connection
     */
    @GetMapping("/test-gemini")
    public ResponseEntity<Map<String, String>> testGemini() {
        String result = geminiService.testConnection();
        return ResponseEntity.ok(Map.of(
            "status", result != null ? "OK" : "FAILED",
            "response", result != null ? result : "Connection failed"
        ));
    }

    /**
     * SSE stream endpoint.
     * Frontend connects: GET /api/agents/stream/{sessionId}
     * Returns a stream of agent-update events until pipeline completes.
     *
     * sessionId format: "task-{taskId}" or "panic-{panicSessionId}" or raw taskId
     */
    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAgentUpdates(@PathVariable String sessionId) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized");
        }

        // Validate that this user owns the resource represented by sessionId
        if (!validateSessionOwnership(sessionId, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to session " + sessionId);
        }

        return agentOrchestrator.registerEmitter(sessionId);
    }

    /**
     * Trigger planning pipeline manually.
     * Frontend should NOT call this — it's called server-side by TaskController.
     * Exposed as endpoint for testing only.
     */
    @PostMapping("/trigger/{taskId}")
    public ResponseEntity<Map<String, String>> triggerPlanning(@PathVariable String taskId) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        com.zerohour.models.Task task = firestoreService.getTaskById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (!task.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        String sessionId = "task-" + taskId;
        agentOrchestrator.runPlanningPipelineAsync(taskId, sessionId);
        return ResponseEntity.accepted().body(Map.of("sessionId", sessionId));
    }

    /**
     * Trigger confirmation pipeline (SchedulerAgent → NudgeAgent).
     * Called when user confirms a task plan.
     */
    @PostMapping("/confirm/{taskId}")
    public ResponseEntity<Map<String, String>> triggerConfirmation(@PathVariable String taskId) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        com.zerohour.models.Task task = firestoreService.getTaskById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (!task.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        String sessionId = "confirm-" + taskId;
        agentOrchestrator.runConfirmationPipelineAsync(taskId, userId, sessionId);
        return ResponseEntity.accepted().body(Map.of("sessionId", sessionId));
    }

    private boolean validateSessionOwnership(String sessionId, String userId) {
        if (sessionId == null) return false;
        
        String idPart = sessionId;
        boolean isPanic = false;
        
        if (sessionId.startsWith("task-")) {
            idPart = sessionId.substring("task-".length());
        } else if (sessionId.startsWith("confirm-")) {
            idPart = sessionId.substring("confirm-".length());
        } else if (sessionId.startsWith("reprioritize-")) {
            idPart = sessionId.substring("reprioritize-".length());
        } else if (sessionId.startsWith("panic-")) {
            isPanic = true;
            idPart = sessionId.substring("panic-".length());
            if (idPart.startsWith("regen-")) {
                idPart = idPart.substring("regen-".length());
                int dashIndex = idPart.indexOf("-");
                if (dashIndex != -1) {
                    idPart = idPart.substring(0, dashIndex);
                }
            }
        }
        
        if (isPanic) {
            com.zerohour.models.PanicSession session = firestoreService.getPanicSession(idPart);
            return session != null && userId.equals(session.getUserId());
        } else {
            // Try fetching as task first
            com.zerohour.models.Task task = firestoreService.getTaskById(idPart);
            if (task != null) {
                return userId.equals(task.getUserId());
            }
            // Fallback: see if it's a panic session instead
            com.zerohour.models.PanicSession session = firestoreService.getPanicSession(idPart);
            return session != null && userId.equals(session.getUserId());
        }
    }
}
