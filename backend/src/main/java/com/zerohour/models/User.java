package com.zerohour.models;

import java.util.Date;
import java.util.Map;

public class User {
    private String uid;
    private String email;
    private String displayName;
    private String googleCalendarId;
    private String googleAccessToken;
    private String googleRefreshToken;
    private Map<String, Object> preferences;
    private Boolean onboarded;
    private Date createdAt;

    // Constructors
    public User() {}

    public User(String uid, String email, String displayName, String googleCalendarId,
                String googleAccessToken, String googleRefreshToken,
                Map<String, Object> preferences, Boolean onboarded, Date createdAt) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.googleCalendarId = googleCalendarId;
        this.googleAccessToken = googleAccessToken;
        this.googleRefreshToken = googleRefreshToken;
        this.preferences = preferences;
        this.onboarded = onboarded;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getGoogleCalendarId() { return googleCalendarId; }
    public void setGoogleCalendarId(String googleCalendarId) { this.googleCalendarId = googleCalendarId; }

    public String getGoogleAccessToken() { return googleAccessToken; }
    public void setGoogleAccessToken(String googleAccessToken) { this.googleAccessToken = googleAccessToken; }

    public String getGoogleRefreshToken() { return googleRefreshToken; }
    public void setGoogleRefreshToken(String googleRefreshToken) { this.googleRefreshToken = googleRefreshToken; }

    public Map<String, Object> getPreferences() { return preferences; }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }

    public Boolean getOnboarded() { return onboarded; }
    public void setOnboarded(Boolean onboarded) { this.onboarded = onboarded; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    // Builder Pattern
    public static UserBuilder builder() {
        return new UserBuilder();
    }

    public static class UserBuilder {
        private String uid;
        private String email;
        private String displayName;
        private String googleCalendarId;
        private String googleAccessToken;
        private String googleRefreshToken;
        private Map<String, Object> preferences;
        private Boolean onboarded;
        private Date createdAt;

        public UserBuilder uid(String uid) { this.uid = uid; return this; }
        public UserBuilder email(String email) { this.email = email; return this; }
        public UserBuilder displayName(String displayName) { this.displayName = displayName; return this; }
        public UserBuilder googleCalendarId(String googleCalendarId) { this.googleCalendarId = googleCalendarId; return this; }
        public UserBuilder googleAccessToken(String googleAccessToken) { this.googleAccessToken = googleAccessToken; return this; }
        public UserBuilder googleRefreshToken(String googleRefreshToken) { this.googleRefreshToken = googleRefreshToken; return this; }
        public UserBuilder preferences(Map<String, Object> preferences) { this.preferences = preferences; return this; }
        public UserBuilder onboarded(Boolean onboarded) { this.onboarded = onboarded; return this; }
        public UserBuilder createdAt(Date createdAt) { this.createdAt = createdAt; return this; }

        public User build() {
            return new User(uid, email, displayName, googleCalendarId, googleAccessToken, googleRefreshToken, preferences, onboarded, createdAt);
        }
    }
}
