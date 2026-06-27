package com.zerohour.models;

public class Subtask {
    private String id;
    private String taskId;
    private String panicSessionId; // nullable
    private String title;
    private int durationMinutes;
    private String status; // PENDING | DONE
    private int orderIndex;
    private String priority; // CRITICAL | HIGH | MEDIUM | LOW
    private String priorityReason;
    private String googleEventId; // nullable

    // Constructors
    public Subtask() {}

    public Subtask(String id, String taskId, String panicSessionId, String title, int durationMinutes,
                   String status, int orderIndex, String priority, String priorityReason, String googleEventId) {
        this.id = id;
        this.taskId = taskId;
        this.panicSessionId = panicSessionId;
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.status = status;
        this.orderIndex = orderIndex;
        this.priority = priority;
        this.priorityReason = priorityReason;
        this.googleEventId = googleEventId;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getPanicSessionId() { return panicSessionId; }
    public void setPanicSessionId(String panicSessionId) { this.panicSessionId = panicSessionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getPriorityReason() { return priorityReason; }
    public void setPriorityReason(String priorityReason) { this.priorityReason = priorityReason; }

    public String getGoogleEventId() { return googleEventId; }
    public void setGoogleEventId(String googleEventId) { this.googleEventId = googleEventId; }

    // Builder Pattern
    public static SubtaskBuilder builder() {
        return new SubtaskBuilder();
    }

    public static class SubtaskBuilder {
        private String id;
        private String taskId;
        private String panicSessionId;
        private String title;
        private int durationMinutes;
        private String status;
        private int orderIndex;
        private String priority;
        private String priorityReason;
        private String googleEventId;

        public SubtaskBuilder id(String id) { this.id = id; return this; }
        public SubtaskBuilder taskId(String taskId) { this.taskId = taskId; return this; }
        public SubtaskBuilder panicSessionId(String panicSessionId) { this.panicSessionId = panicSessionId; return this; }
        public SubtaskBuilder title(String title) { this.title = title; return this; }
        public SubtaskBuilder durationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; return this; }
        public SubtaskBuilder status(String status) { this.status = status; return this; }
        public SubtaskBuilder orderIndex(int orderIndex) { this.orderIndex = orderIndex; return this; }
        public SubtaskBuilder priority(String priority) { this.priority = priority; return this; }
        public SubtaskBuilder priorityReason(String priorityReason) { this.priorityReason = priorityReason; return this; }
        public SubtaskBuilder googleEventId(String googleEventId) { this.googleEventId = googleEventId; return this; }

        public Subtask build() {
            return new Subtask(id, taskId, panicSessionId, title, durationMinutes, status, orderIndex, priority, priorityReason, googleEventId);
        }
    }
}
