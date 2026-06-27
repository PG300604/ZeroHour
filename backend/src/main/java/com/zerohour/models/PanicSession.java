package com.zerohour.models;

import java.util.Date;

public class PanicSession {
    private String id;
    private String userId;
    private String rawInput;
    private String conversationJson; // serialized chat history
    private String generatedPlanJson; // serialized subtask array
    private boolean confirmed;
    private String status; // QUESTIONING | GENERATING | PLAN_READY | CONFIRMED
    private String sseSessionId; // SSE session ID for live agent updates
    private Date createdAt;
    private String title;

    // Constructors
    public PanicSession() {}

    public PanicSession(String id, String userId, String rawInput, String conversationJson,
                        String generatedPlanJson, boolean confirmed, String status,
                        String sseSessionId, Date createdAt, String title) {
        this.id = id;
        this.userId = userId;
        this.rawInput = rawInput;
        this.conversationJson = conversationJson;
        this.generatedPlanJson = generatedPlanJson;
        this.confirmed = confirmed;
        this.status = status;
        this.sseSessionId = sseSessionId;
        this.createdAt = createdAt;
        this.title = title;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRawInput() { return rawInput; }
    public void setRawInput(String rawInput) { this.rawInput = rawInput; }

    public String getConversationJson() { return conversationJson; }
    public void setConversationJson(String conversationJson) { this.conversationJson = conversationJson; }

    public String getGeneratedPlanJson() { return generatedPlanJson; }
    public void setGeneratedPlanJson(String generatedPlanJson) { this.generatedPlanJson = generatedPlanJson; }

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSseSessionId() { return sseSessionId; }
    public void setSseSessionId(String sseSessionId) { this.sseSessionId = sseSessionId; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    // Builder Pattern
    public static PanicSessionBuilder builder() {
        return new PanicSessionBuilder();
    }

    public static class PanicSessionBuilder {
        private String id;
        private String userId;
        private String rawInput;
        private String conversationJson;
        private String generatedPlanJson;
        private boolean confirmed;
        private String status;
        private String sseSessionId;
        private Date createdAt;
        private String title;

        public PanicSessionBuilder id(String id) { this.id = id; return this; }
        public PanicSessionBuilder userId(String userId) { this.userId = userId; return this; }
        public PanicSessionBuilder rawInput(String rawInput) { this.rawInput = rawInput; return this; }
        public PanicSessionBuilder conversationJson(String conversationJson) { this.conversationJson = conversationJson; return this; }
        public PanicSessionBuilder generatedPlanJson(String generatedPlanJson) { this.generatedPlanJson = generatedPlanJson; return this; }
        public PanicSessionBuilder confirmed(boolean confirmed) { this.confirmed = confirmed; return this; }
        public PanicSessionBuilder status(String status) { this.status = status; return this; }
        public PanicSessionBuilder sseSessionId(String sseSessionId) { this.sseSessionId = sseSessionId; return this; }
        public PanicSessionBuilder createdAt(Date createdAt) { this.createdAt = createdAt; return this; }
        public PanicSessionBuilder title(String title) { this.title = title; return this; }

        public PanicSession build() {
            return new PanicSession(id, userId, rawInput, conversationJson, generatedPlanJson,
                    confirmed, status, sseSessionId, createdAt, title);
        }
    }
}
