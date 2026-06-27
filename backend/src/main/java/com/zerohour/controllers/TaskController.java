package com.zerohour.controllers;

import com.zerohour.agents.AgentOrchestrator;
import com.zerohour.agents.PrioritizerAgent;
import com.zerohour.models.CalendarEvent;
import com.zerohour.models.Subtask;
import com.zerohour.models.Task;
import com.zerohour.services.AuthService;
import com.zerohour.services.CalendarService;
import com.zerohour.services.FirestoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * TaskController — REST API for Task CRUD + Agent Pipeline Triggers (F03, F05, F06).
 *
 * Endpoints:
 *   GET    /api/tasks                        — List all tasks for current user
 *   POST   /api/tasks                        — Create task, trigger PlannerAgent
 *   GET    /api/tasks/{id}                   — Get task details
 *   PUT    /api/tasks/{id}                   — Update task fields
 *   DELETE /api/tasks/{id}                   — Delete task + cascade subtasks, calendar, nudges
 *   POST   /api/tasks/{id}/confirm           — Trigger SchedulerAgent + NudgeAgent
 *   POST   /api/tasks/{id}/reprioritize      — Re-trigger PrioritizerAgent on subtasks
 *   GET    /api/tasks/{id}/subtasks           — List subtasks for a task
 *   PUT    /api/tasks/{id}/subtasks/{sid}     — Update individual subtask
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    private AuthService authService;

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private AgentOrchestrator agentOrchestrator;

    @Autowired
    private PrioritizerAgent prioritizerAgent;

    // ─── LIST TASKS ────────────────────────────────────────────────────────

    /**
     * GET /api/tasks — List all tasks for current user.
     * Returns tasks ordered by createdAt DESC.
     */
    @GetMapping
    public ResponseEntity<List<Task>> getAllTasks() {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(firestoreService.getTasksByUserId(userId));
    }

    // ─── CREATE TASK ───────────────────────────────────────────────────────

    /**
     * POST /api/tasks — Create a new task and trigger the agent planning pipeline.
     * PlannerAgent → PrioritizerAgent runs async, streamed via SSE.
     *
     * Request:  { "title", "description", "deadline" }
     * Response: { "task", "sessionId" }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody TaskRequest request) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Task task = Task.builder()
                .userId(userId)
                .title(request.getTitle())
                .description(request.getDescription())
                .status("PENDING")
                .priority("MEDIUM")
                .deadline(parseDeadline(request.getDeadline()))
                .createdAt(new Date())
                .build();

        Task saved = firestoreService.saveTask(task);

        // Trigger agent planning pipeline asynchronously
        String sessionId = "task-" + saved.getId();
        agentOrchestrator.runPlanningPipelineAsync(saved.getId(), sessionId);

        // Return task + sessionId so frontend can connect to SSE stream
        Map<String, Object> response = new HashMap<>();
        response.put("task", saved);
        response.put("sessionId", sessionId);
        return ResponseEntity.ok(response);
    }

    // ─── GET TASK ──────────────────────────────────────────────────────────

    /**
     * GET /api/tasks/{id} — Get task details including subtask count.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable String id) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Task task = firestoreService.getTaskById(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (!task.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Subtask> subtasks = firestoreService.getSubtasksByTaskId(id);
        List<CalendarEvent> calendarEvents = firestoreService.getCalendarEventsByTaskId(id);
        List<com.zerohour.models.Nudge> nudges = firestoreService.getNudgesByTaskId(id);

        Map<String, Object> response = new HashMap<>();
        response.put("task", task);
        response.put("subtasks", subtasks);
        response.put("calendarEvents", calendarEvents);
        response.put("nudges", nudges);
        response.put("subtaskCount", subtasks.size());
        response.put("completedSubtasks", subtasks.stream()
                .filter(s -> "DONE".equals(s.getStatus())).count());
        return ResponseEntity.ok(response);
    }

    // ─── UPDATE TASK ───────────────────────────────────────────────────────

    /**
     * PUT /api/tasks/{id} — Update task fields (title, description, deadline, status, priority).
     */
    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable String id, @RequestBody TaskRequest request) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Task existing = firestoreService.getTaskById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (request.getTitle() != null) {
            existing.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getDeadline() != null) {
            existing.setDeadline(parseDeadline(request.getDeadline()));
        }
        if (request.getStatus() != null) {
            existing.setStatus(request.getStatus());
        }
        if (request.getPriority() != null) {
            existing.setPriority(request.getPriority());
        }

        Task saved = firestoreService.saveTask(existing);
        return ResponseEntity.ok(saved);
    }

    // ─── DELETE TASK ───────────────────────────────────────────────────────

    /**
     * DELETE /api/tasks/{id} — Delete task, cascade subtasks, calendar events, nudges.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Task existing = firestoreService.getTaskById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 1. Get calendar events for task → delete from Google Calendar
        List<CalendarEvent> events = firestoreService.getCalendarEventsByTaskId(id);
        if (events != null) {
            for (CalendarEvent event : events) {
                try {
                    calendarService.deleteCalendarEvent(userId, event.getGoogleEventId());
                } catch (Exception e) {
                    log.warn("Failed to delete calendar event {}: {}", event.getGoogleEventId(), e.getMessage());
                }
            }
        }

        // 2. firestoreService.deleteTask(taskId) — cascades subtasks + nudges
        firestoreService.deleteTask(id);

        return ResponseEntity.noContent().build();
    }

    // ─── CONFIRM TASK ──────────────────────────────────────────────────────

    /**
     * POST /api/tasks/{id}/confirm — Trigger SchedulerAgent + NudgeAgent pipeline.
     * Called when user confirms the AI-generated plan.
     *
     * Response: { "status", "sessionId" }
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<Map<String, String>> confirmTask(@PathVariable String id) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Task task = firestoreService.getTaskById(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (!task.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Trigger confirmation pipeline asynchronously
        String sessionId = "confirm-" + id;
        agentOrchestrator.runConfirmationPipelineAsync(id, userId, sessionId);

        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("sessionId", sessionId);
        return ResponseEntity.ok(response);
    }

    // ─── REPRIORITIZE TASK ─────────────────────────────────────────────────

    /**
     * POST /api/tasks/{id}/reprioritize — Re-run PrioritizerAgent on existing subtasks.
     * Useful when user edits subtasks and wants updated priority rankings.
     *
     * Response: { "status", "sessionId", "subtasks" }
     */
    @PostMapping("/{id}/reprioritize")
    public ResponseEntity<Map<String, Object>> reprioritize(@PathVariable String id) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Task task = firestoreService.getTaskById(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (!task.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Get existing subtasks
        List<Subtask> subtasks = firestoreService.getSubtasksByTaskId(id);
        if (subtasks.isEmpty()) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("error", "No subtasks to reprioritize");
            return ResponseEntity.badRequest().body(errorResp);
        }

        // Run PrioritizerAgent synchronously (it's fast)
        String sessionId = "reprioritize-" + id;
        agentOrchestrator.emit(sessionId, "PrioritizerAgent", "THINKING", "Re-ranking subtasks...");

        List<Subtask> prioritized = prioritizerAgent.prioritize(subtasks, id, sessionId);

        // Save re-prioritized subtasks
        firestoreService.saveAllSubtasks(prioritized);

        // Update parent task priority
        String taskPriority = prioritizerAgent.determineOverallPriority(prioritized, task.getPriority());
        task.setPriority(taskPriority);
        firestoreService.saveTask(task);

        agentOrchestrator.emit(sessionId, "PrioritizerAgent", "DONE", "Re-prioritization complete");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("sessionId", sessionId);
        response.put("subtasks", prioritized);
        return ResponseEntity.ok(response);
    }

    // ─── LIST SUBTASKS ─────────────────────────────────────────────────────

    /**
     * GET /api/tasks/{id}/subtasks — Get all subtasks for a task, ordered by orderIndex.
     */
    @GetMapping("/{id}/subtasks")
    public ResponseEntity<List<Subtask>> getSubtasks(@PathVariable String id) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Task task = firestoreService.getTaskById(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (!task.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Subtask> subtasks = firestoreService.getSubtasksByTaskId(id);
        return ResponseEntity.ok(subtasks);
    }

    // ─── UPDATE SUBTASK ────────────────────────────────────────────────────

    /**
     * PUT /api/tasks/{id}/subtasks/{subtaskId} — Update a specific subtask.
     * Accepts partial fields: title, durationMinutes, status, priority, orderIndex.
     */
    @PutMapping("/{id}/subtasks/{subtaskId}")
    public ResponseEntity<Map<String, Object>> updateSubtask(
            @PathVariable String id,
            @PathVariable String subtaskId,
            @RequestBody Map<String, Object> updates) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Task task = firestoreService.getTaskById(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (!task.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Verify subtask belongs to this task
        Subtask subtask = firestoreService.getSubtaskById(subtaskId);
        if (subtask == null || !id.equals(subtask.getTaskId())) {
            return ResponseEntity.notFound().build();
        }

        // Apply partial updates
        if (updates.containsKey("title")) subtask.setTitle((String) updates.get("title"));
        if (updates.containsKey("durationMinutes")) subtask.setDurationMinutes((Integer) updates.get("durationMinutes"));
        if (updates.containsKey("status")) subtask.setStatus((String) updates.get("status"));
        if (updates.containsKey("priority")) subtask.setPriority((String) updates.get("priority"));
        if (updates.containsKey("orderIndex")) subtask.setOrderIndex((Integer) updates.get("orderIndex"));
        if (updates.containsKey("priorityReason")) subtask.setPriorityReason((String) updates.get("priorityReason"));

        firestoreService.saveSubtask(subtask);

        // Recalculate parent task status based on all subtasks status
        List<Subtask> allSubtasks = firestoreService.getSubtasksByTaskId(id);
        if (!allSubtasks.isEmpty()) {
            boolean allDone = allSubtasks.stream().allMatch(s -> "DONE".equals(s.getStatus()));
            boolean allPending = allSubtasks.stream().allMatch(s -> "PENDING".equals(s.getStatus()) || s.getStatus() == null);
            
            String newStatus;
            if (allDone) {
                newStatus = "DONE";
            } else if (allPending) {
                newStatus = "PENDING";
            } else {
                newStatus = "IN_PROGRESS";
            }
            
            if (!newStatus.equals(task.getStatus())) {
                task.setStatus(newStatus);
                firestoreService.saveTask(task);
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("subtask", subtask);
        resp.put("taskStatus", task.getStatus());
        return ResponseEntity.ok(resp);
    }

    // ─── HELPER METHODS ────────────────────────────────────────────────────

    private Date parseDeadline(String deadlineStr) {
        if (deadlineStr == null || deadlineStr.isEmpty()) {
            return null;
        }
        try {
            return Date.from(java.time.Instant.parse(deadlineStr));
        } catch (Exception e) {
            try {
                return Date.from(java.time.ZonedDateTime.parse(deadlineStr).toInstant());
            } catch (Exception ex) {
                try {
                    return Date.from(java.time.OffsetDateTime.parse(deadlineStr).toInstant());
                } catch (Exception ex2) {
                    try {
                        return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(deadlineStr);
                    } catch (Exception ex3) {
                        log.error("Failed to parse deadline: {}", deadlineStr, ex3);
                        return null;
                    }
                }
            }
        }
    }

    // ─── REQUEST DTO ───────────────────────────────────────────────────────

    public static class TaskRequest {
        private String title;
        private String description;
        private String deadline;
        private String status;
        private String priority;

        public TaskRequest() {}

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getDeadline() { return deadline; }
        public void setDeadline(String deadline) { this.deadline = deadline; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }
}
