package com.zerohour.models;

import java.util.Date;

public class Nudge {
    private String id;
    private String taskId;
    private String type; // REMINDER_24H | REMINDER_6H | REMINDER_1H
    private String channel; // IN_APP | EMAIL | BOTH
    private Date scheduledAt;
    private boolean sent;
    private boolean read;

    // Constructors
    public Nudge() {}

    public Nudge(String id, String taskId, String type, String channel, Date scheduledAt,
                 boolean sent, boolean read) {
        this.id = id;
        this.taskId = taskId;
        this.type = type;
        this.channel = channel;
        this.scheduledAt = scheduledAt;
        this.sent = sent;
        this.read = read;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public Date getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Date scheduledAt) { this.scheduledAt = scheduledAt; }

    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    // Builder Pattern
    public static NudgeBuilder builder() {
        return new NudgeBuilder();
    }

    public static class NudgeBuilder {
        private String id;
        private String taskId;
        private String type;
        private String channel;
        private Date scheduledAt;
        private boolean sent;
        private boolean read;

        public NudgeBuilder id(String id) { this.id = id; return this; }
        public NudgeBuilder taskId(String taskId) { this.taskId = taskId; return this; }
        public NudgeBuilder type(String type) { this.type = type; return this; }
        public NudgeBuilder channel(String channel) { this.channel = channel; return this; }
        public NudgeBuilder scheduledAt(Date scheduledAt) { this.scheduledAt = scheduledAt; return this; }
        public NudgeBuilder sent(boolean sent) { this.sent = sent; return this; }
        public NudgeBuilder read(boolean read) { this.read = read; return this; }

        public Nudge build() {
            return new Nudge(id, taskId, type, channel, scheduledAt, sent, read);
        }
    }
}
