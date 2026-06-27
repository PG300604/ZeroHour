package com.zerohour.agents;

import com.zerohour.models.CalendarEvent;
import com.zerohour.models.Subtask;
import com.zerohour.models.Task;
import com.zerohour.models.User;
import com.zerohour.services.CalendarService;
import com.zerohour.services.FirestoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * SchedulerAgent — Pushes confirmed subtasks to Google Calendar.
 *
 * Day 4 hardening:
 * - Retry with exponential backoff on Calendar API failures (up to 3 attempts)
 * - Per-subtask error isolation (one failure doesn't stop the batch)
 */
@Service
public class SchedulerAgent {

    private static final Logger log = LoggerFactory.getLogger(SchedulerAgent.class);
    private static final int MAX_RETRIES = 3;

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private FirestoreService firestoreService;

    @org.springframework.context.annotation.Lazy
    @Autowired
    private AgentOrchestrator agentOrchestrator;

    // ─── ORCHESTRATOR API ──────────────────────────────────────────────────

    /**
     * Schedule all confirmed subtasks to Google Calendar with retry logic.
     * Fetches task, user, and subtasks from Firestore, then creates calendar events.
     * Retries up to 3 times with exponential backoff on failure.
     *
     * @param taskId    The task ID to schedule
     * @param userId    The user ID who owns the task
     * @param sessionId SSE session ID (for logging)
     * @return number of calendar events created
     */
    public int scheduleTask(String taskId, String userId, String sessionId) {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            try {
                Task task = firestoreService.getTask(taskId);
                if (task == null) {
                    log.warn("SchedulerAgent: Task {} not found", taskId);
                    return 0;
                }

                User user = firestoreService.getUser(userId);
                if (user == null) {
                    log.warn("SchedulerAgent: User {} not found", userId);
                    return 0;
                }

                List<Subtask> subtasks = firestoreService.getSubtasksByTaskId(taskId);
                if (subtasks == null || subtasks.isEmpty()) {
                    log.info("SchedulerAgent: No subtasks to schedule for task {}", taskId);
                    return 0;
                }

                // Delegate to the existing scheduling logic
                int result = scheduleSubtasksInternal(user, task, subtasks);
                log.info("SchedulerAgent: Successfully scheduled {} events for task {} on attempt {}",
                        result, taskId, attempt + 1);
                return result;

            } catch (Exception e) {
                attempt++;
                log.error("SchedulerAgent attempt {} failed for task {}: {}",
                        attempt, taskId, e.getMessage(), e);

                if (attempt < MAX_RETRIES) {
                    try {
                        // Exponential backoff: 1s, 2s, 4s
                        long sleepMs = 1000L * (long) Math.pow(2, attempt - 1);
                        log.info("SchedulerAgent: Retrying in {}ms...", sleepMs);
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("SchedulerAgent: Retry interrupted");
                        break;
                    }
                }
            }
        }

        log.error("SchedulerAgent: All {} attempts failed for task {}", MAX_RETRIES, taskId);
        return 0; // Don't crash — return 0 events created
    }

    // ─── BACKWARD-COMPATIBLE API ───────────────────────────────────────────

    /**
     * Schedule subtasks to Google Calendar with SSE progress logging.
     * Used by existing controllers (TaskController, PanicController).
     */
    public void scheduleSubtasks(String sessionId, User user, Task task, List<Subtask> subtasks) {
        log.info("SchedulerAgent: Starting scheduling for session {} / task {}", sessionId, task.getId());
        agentOrchestrator.logProgress(sessionId, "SchedulerAgent", "THINKING", "Pushing tasks to Google Calendar...");

        if (subtasks == null || subtasks.isEmpty()) {
            agentOrchestrator.logProgress(sessionId, "SchedulerAgent", "DONE", "No subtasks to schedule.");
            return;
        }

        int scheduledCount = scheduleSubtasksInternal(user, task, subtasks);

        agentOrchestrator.logProgress(
                sessionId,
                "SchedulerAgent",
                "DONE",
                String.format("%d calendar events successfully created.", scheduledCount)
        );
    }

    // ─── INTERNAL SCHEDULING LOGIC ─────────────────────────────────────────

    /**
     * Core scheduling logic. Packs subtasks consecutively from current time.
     * Returns the number of events successfully created.
     * Per-subtask error isolation — one failure doesn't stop the batch.
     */
    private int scheduleSubtasksInternal(User user, Task task, List<Subtask> subtasks) {
        int scheduledCount = 0;
        long currentTimeMillis = System.currentTimeMillis();

        for (Subtask subtask : subtasks) {
            try {
                Date startTime = new Date(currentTimeMillis);
                long durationMillis = (long) subtask.getDurationMinutes() * 60 * 1000;
                Date endTime = new Date(currentTimeMillis + durationMillis);

                String description = String.format(
                        "Auto-scheduled by ZeroHour AI • Task: %s\nPriority: %s\nRationale: %s",
                        task.getTitle(),
                        subtask.getPriority() != null ? subtask.getPriority() : "MEDIUM",
                        subtask.getPriorityReason() != null ? subtask.getPriorityReason() : "Part of ZeroHour schedule."
                );

                Map<String, String> eventDetails = calendarService.createCalendarEvent(
                        user,
                        subtask.getTitle(),
                        description,
                        startTime,
                        endTime
                );

                if (eventDetails != null && eventDetails.get("eventId") != null) {
                    CalendarEvent event = CalendarEvent.builder()
                            .googleEventId(eventDetails.get("eventId"))
                            .taskId(task.getId())
                            .startTime(startTime)
                            .endTime(endTime)
                            .calendarId(eventDetails.get("calendarId"))
                            .build();
                    firestoreService.saveCalendarEvent(event);
                    scheduledCount++;
                    log.debug("SchedulerAgent: Created event for subtask '{}'", subtask.getTitle());
                } else {
                    log.warn("SchedulerAgent: No eventId returned for subtask '{}', skipping", subtask.getTitle());
                }

                // Pack consecutively
                currentTimeMillis += durationMillis;

            } catch (Exception e) {
                log.error("SchedulerAgent: Failed to schedule subtask '{}': {}",
                        subtask.getTitle(), e.getMessage());
                // Continue to next subtask — don't break the batch
                currentTimeMillis += (long) subtask.getDurationMinutes() * 60 * 1000;
            }
        }

        return scheduledCount;
    }
}
