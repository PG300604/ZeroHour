package com.zerohour.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerohour.models.PanicSession;
import com.zerohour.models.Subtask;
import com.zerohour.services.AuthService;
import com.zerohour.services.FirestoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent Pipeline Orchestrator.
 * Routes intent to agents, manages SSE emission, runs async agent pipelines.
 *
 * Day 4 hardening:
 * - Descriptive SSE messages for judge-facing UI
 * - Agent timing + timeout warnings
 * - SYSTEM COMPLETE summary event
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private static final long AGENT_TIMEOUT_MS = 30_000;

    @Autowired private PlannerAgent plannerAgent;
    @Autowired private PrioritizerAgent prioritizerAgent;
    @Autowired private SchedulerAgent schedulerAgent;
    @Autowired private NudgeAgent nudgeAgent;
    @Autowired private FirestoreService firestoreService;
    @Autowired private AuthService authService;

    private final Map<String, List<Map<String, String>>> logHistory = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Send periodic heartbeats to all active SSE emitters to prevent Cloud Run timeouts.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 25_000)
    public void sendHeartbeats() {
        emitters.forEach((sessionId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(Map.of("ts", System.currentTimeMillis())));
                } catch (Exception e) {
                    removeEmitter(sessionId, emitter);
                }
            }
        });
    }

    // ─── SSE EMITTER MANAGEMENT ────────────────────────────────────────────

    public SseEmitter registerEmitter(String sessionId) {
        log.info("Registering SSE emitter for session {}", sessionId);
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        emitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(sessionId, emitter));
        emitter.onTimeout(() -> removeEmitter(sessionId, emitter));
        emitter.onError(e -> removeEmitter(sessionId, emitter));

        // Replay history for late-connecting clients
        List<Map<String, String>> history = logHistory.get(sessionId);
        if (history != null) {
            for (Map<String, String> event : history) {
                try {
                    emitter.send(SseEmitter.event().name("agent-update").data(event));
                } catch (IOException e) {
                    log.warn("Failed to send history to emitter in session {}: {}", sessionId, e.getMessage());
                    removeEmitter(sessionId, emitter);
                    break;
                }
            }
        } else {
            try {
                Map<String, String> initEvent = new HashMap<>();
                initEvent.put("agent", "System");
                initEvent.put("status", "CONNECTED");
                initEvent.put("message", "Agent orchestration log stream active.");
                emitter.send(SseEmitter.event().name("agent-update").data(initEvent));
            } catch (IOException e) {
                removeEmitter(sessionId, emitter);
            }
        }

        return emitter;
    }

    private void removeEmitter(String sessionId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(sessionId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(sessionId);
            }
        }
    }

    // ─── SSE EMISSION ──────────────────────────────────────────────────────

    public void emit(String sessionId, String agentName, String status, String message) {
        emit(sessionId, agentName, status, message, null);
    }

    public void emit(String sessionId, String agentName, String status, String message, String payload) {
        log.info("AgentLog [{}] -> Agent: {}, Status: {}, Message: {}", sessionId, agentName, status, message);

        Map<String, String> event = new HashMap<>();
        event.put("agent", agentName);
        event.put("status", status);
        event.put("message", message);
        if (payload != null) {
            event.put("payload", payload);
        }
        event.put("timestamp", String.valueOf(System.currentTimeMillis()));

        logHistory.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(event);

        List<SseEmitter> activeEmitters = emitters.get(sessionId);
        if (activeEmitters != null) {
            List<SseEmitter> failedEmitters = new ArrayList<>();
            for (SseEmitter emitter : activeEmitters) {
                try {
                    emitter.send(SseEmitter.event().name("agent-update").data(event));
                } catch (IOException e) {
                    log.warn("Failed to send live update to emitter in session {}, removing.", sessionId);
                    failedEmitters.add(emitter);
                }
            }
            for (SseEmitter failed : failedEmitters) {
                removeEmitter(sessionId, failed);
            }
        }
    }

    public void logProgress(String sessionId, String agentName, String status, String message) {
        emit(sessionId, agentName, status, message);
    }

    // ─── ASYNC PIPELINE: PLANNING ──────────────────────────────────────────

    @Async("sseTaskExecutor")
    public void runPlanningPipelineAsync(String taskId, String sessionId) {
        try {
            clearLogs(sessionId);

            // Step 1: PlannerAgent
            emit(sessionId, "PlannerAgent", "THINKING", "Analyzing your goal and deadline...");
            long agentStart = System.currentTimeMillis();

            List<Subtask> subtasks = plannerAgent.generateSubtasks(taskId, sessionId);

            long agentDuration = System.currentTimeMillis() - agentStart;
            logAgentTiming("PlannerAgent", agentDuration);

            String subtasksJson = serializeSubtasks(subtasks);
            emit(sessionId, "PlannerAgent", "DONE",
                    "Generated " + subtasks.size() + " actionable subtasks ✓", subtasksJson);

            // Step 2: PrioritizerAgent
            emit(sessionId, "PrioritizerAgent", "THINKING", "Ranking subtasks by urgency × impact...");
            agentStart = System.currentTimeMillis();

            List<Subtask> prioritized = prioritizerAgent.prioritize(subtasks, taskId, sessionId);

            agentDuration = System.currentTimeMillis() - agentStart;
            logAgentTiming("PrioritizerAgent", agentDuration);

            long critical = prioritized.stream()
                    .filter(s -> "CRITICAL".equals(s.getPriority())).count();
            emit(sessionId, "PrioritizerAgent", "DONE",
                    critical + " critical tasks identified ✓ " + buildPriorityResultMessage(prioritized));

            // Update parent task priority
            String taskPriority = prioritizerAgent.determineOverallPriority(prioritized, "MEDIUM");
            com.zerohour.models.Task task = firestoreService.getTask(taskId);
            if (task != null) {
                task.setPriority(taskPriority);
                firestoreService.saveTask(task);
            }

            // Step 3: Waiting for confirmation
            emit(sessionId, "SchedulerAgent", "WAITING",
                    "Ready to push to Google Calendar on your confirmation");
            emit(sessionId, "NudgeAgent", "WAITING",
                    "Will schedule 24h, 6h, and 1h reminders on confirmation");

            // Completion summary
            emit(sessionId, "SYSTEM", "COMPLETE",
                    "All agents done — your plan is ready!");

            completeEmitters(sessionId);

        } catch (Exception e) {
            log.error("Planning pipeline failed for task {}", taskId, e);
            emit(sessionId, "System", "ERROR", "Something went wrong: " + e.getMessage());
        }
    }

    // ─── ASYNC PIPELINE: CONFIRMATION ──────────────────────────────────────

    @Async("sseTaskExecutor")
    public void runConfirmationPipelineAsync(String taskId, String userId, String sessionId) {
        try {
            // Step 1: SchedulerAgent
            emit(sessionId, "SchedulerAgent", "THINKING", "Creating Google Calendar events...");
            long agentStart = System.currentTimeMillis();

            int eventsCreated = schedulerAgent.scheduleTask(taskId, userId, sessionId);

            long agentDuration = System.currentTimeMillis() - agentStart;
            logAgentTiming("SchedulerAgent", agentDuration);

            emit(sessionId, "SchedulerAgent", "DONE",
                    eventsCreated + " events added to your Google Calendar ✓");

            // Step 2: NudgeAgent
            emit(sessionId, "NudgeAgent", "THINKING", "Scheduling deadline reminders...");
            agentStart = System.currentTimeMillis();

            int nudgesScheduled = nudgeAgent.scheduleNudgesForTask(taskId, sessionId);

            agentDuration = System.currentTimeMillis() - agentStart;
            logAgentTiming("NudgeAgent", agentDuration);

            emit(sessionId, "NudgeAgent", "DONE",
                    "Reminders set for 24h, 6h, and 1h before deadline ✓");

            // Completion summary
            emit(sessionId, "SYSTEM", "COMPLETE",
                    "Confirmation complete — " + eventsCreated + " calendar events, "
                    + nudgesScheduled + " reminders scheduled!");

            completeEmitters(sessionId);

        } catch (Exception e) {
            log.error("Confirmation pipeline failed for task {}", taskId, e);
            emit(sessionId, "System", "ERROR", "Confirmation failed: " + e.getMessage());
        }
    }

    // ─── ASYNC PIPELINE: PANIC MODE ────────────────────────────────────────

    @Async("sseTaskExecutor")
    public void runPanicPipelineAsync(String panicSessionId, String sessionId) {
        try {
            clearLogs(sessionId);

            // Step 1: PlannerAgent
            emit(sessionId, "PlannerAgent", "THINKING", "Building your survival plan...");
            long agentStart = System.currentTimeMillis();

            List<Subtask> subtasks = plannerAgent.generateSubtasksFromPanic(panicSessionId, sessionId);

            long agentDuration = System.currentTimeMillis() - agentStart;
            logAgentTiming("PlannerAgent (Panic)", agentDuration);

            String subtasksJson = serializeSubtasks(subtasks);
            emit(sessionId, "PlannerAgent", "DONE",
                    "Created " + subtasks.size() + " survival subtasks ✓", subtasksJson);

            // Step 2: PrioritizerAgent
            emit(sessionId, "PrioritizerAgent", "THINKING", "Ranking by urgency × impact...");
            agentStart = System.currentTimeMillis();

            List<Subtask> prioritized = prioritizerAgent.prioritize(subtasks, null, sessionId);

            agentDuration = System.currentTimeMillis() - agentStart;
            logAgentTiming("PrioritizerAgent (Panic)", agentDuration);

            long critical = prioritized.stream()
                    .filter(s -> "CRITICAL".equals(s.getPriority())).count();
            emit(sessionId, "PrioritizerAgent", "DONE",
                    critical + " critical tasks identified ✓ " + buildPriorityResultMessage(prioritized));

            // Waiting
            emit(sessionId, "SchedulerAgent", "WAITING",
                    "Ready to push to Google Calendar on your confirmation");
            emit(sessionId, "NudgeAgent", "WAITING",
                    "Will schedule 24h, 6h, and 1h reminders on confirmation");

            // Update panic session with generated plan
            PanicSession session = firestoreService.getPanicSession(panicSessionId);
            if (session != null) {
                session.setGeneratedPlanJson(serializeSubtasks(prioritized));
                firestoreService.savePanicSession(session);
            }

            // Completion summary
            emit(sessionId, "SYSTEM", "COMPLETE",
                    "Survival plan ready — review and confirm!");

            completeEmitters(sessionId);

        } catch (Exception e) {
            log.error("Panic pipeline failed for session {}", panicSessionId, e);
            emit(sessionId, "System", "ERROR", "Plan generation failed: " + e.getMessage());
        }
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────

    private void logAgentTiming(String agentName, long durationMs) {
        if (durationMs > AGENT_TIMEOUT_MS) {
            log.warn("⚠ {} took {}ms — possible Gemini latency", agentName, durationMs);
        } else {
            log.info("{} completed in {}ms", agentName, durationMs);
        }
    }

    private void completeEmitters(String sessionId) {
        List<SseEmitter> activeEmitters = emitters.get(sessionId);
        if (activeEmitters != null) {
            for (SseEmitter emitter : activeEmitters) {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("Error completing emitter for session {}", sessionId);
                }
            }
        }
    }

    private String buildPriorityResultMessage(List<Subtask> subtasks) {
        long critical = subtasks.stream().filter(s -> "CRITICAL".equals(s.getPriority())).count();
        long high = subtasks.stream().filter(s -> "HIGH".equals(s.getPriority())).count();
        long medium = subtasks.stream().filter(s -> "MEDIUM".equals(s.getPriority())).count();
        long low = subtasks.stream().filter(s -> "LOW".equals(s.getPriority())).count();
        return String.format("(%d Critical, %d High, %d Medium, %d Low)",
                critical, high, medium, low);
    }

    private String serializeSubtasks(List<Subtask> subtasks) {
        try {
            return new ObjectMapper().writeValueAsString(subtasks);
        } catch (Exception e) {
            return "[]";
        }
    }

    public void clearLogs(String sessionId) {
        logHistory.remove(sessionId);
    }

    public List<Map<String, String>> getHistory(String sessionId) {
        return logHistory.getOrDefault(sessionId, Collections.emptyList());
    }
}
