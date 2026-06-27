package com.zerohour.models;

import java.util.Date;

public class Task {
    private String id;
    private String userId;
    private String title;
    private String description;
    private String status; // PENDING | IN_PROGRESS | DONE | OVERDUE
    private String priority; // CRITICAL | HIGH | MEDIUM | LOW
    private Date deadline;
    private String calendarEventId;
    private Date createdAt;

    // Constructors
    public Task() {}

    public Task(String id, String userId, String title, String description, String status,
                String priority, Date deadline, String calendarEventId, Date createdAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.deadline = deadline;
        this.calendarEventId = calendarEventId;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Date getDeadline() { return deadline; }
    public void setDeadline(Date deadline) { this.deadline = deadline; }

    public String getCalendarEventId() { return calendarEventId; }
    public void setCalendarEventId(String calendarEventId) { this.calendarEventId = calendarEventId; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    // Builder Pattern
    public static TaskBuilder builder() {
        return new TaskBuilder();
    }

    public static class TaskBuilder {
        private String id;
        private String userId;
        private String title;
        private String description;
        private String status;
        private String priority;
        private Date deadline;
        private String calendarEventId;
        private Date createdAt;

        public TaskBuilder id(String id) { this.id = id; return this; }
        public TaskBuilder userId(String userId) { this.userId = userId; return this; }
        public TaskBuilder title(String title) { this.title = title; return this; }
        public TaskBuilder description(String description) { this.description = description; return this; }
        public TaskBuilder status(String status) { this.status = status; return this; }
        public TaskBuilder priority(String priority) { this.priority = priority; return this; }
        public TaskBuilder deadline(Date deadline) { this.deadline = deadline; return this; }
        public TaskBuilder calendarEventId(String calendarEventId) { this.calendarEventId = calendarEventId; return this; }
        public TaskBuilder createdAt(Date createdAt) { this.createdAt = createdAt; return this; }

        public Task build() {
            return new Task(id, userId, title, description, status, priority, deadline, calendarEventId, createdAt);
        }
    }
}
