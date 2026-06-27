package com.zerohour.models;

import java.util.Date;

public class CalendarEvent {
    private String id;
    private String googleEventId;
    private String taskId;
    private Date startTime;
    private Date endTime;
    private String calendarId;

    // Constructors
    public CalendarEvent() {}

    public CalendarEvent(String id, String googleEventId, String taskId, Date startTime,
                         Date endTime, String calendarId) {
        this.id = id;
        this.googleEventId = googleEventId;
        this.taskId = taskId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.calendarId = calendarId;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGoogleEventId() { return googleEventId; }
    public void setGoogleEventId(String googleEventId) { this.googleEventId = googleEventId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public String getCalendarId() { return calendarId; }
    public void setCalendarId(String calendarId) { this.calendarId = calendarId; }

    // Builder Pattern
    public static CalendarEventBuilder builder() {
        return new CalendarEventBuilder();
    }

    public static class CalendarEventBuilder {
        private String id;
        private String googleEventId;
        private String taskId;
        private Date startTime;
        private Date endTime;
        private String calendarId;

        public CalendarEventBuilder id(String id) { this.id = id; return this; }
        public CalendarEventBuilder googleEventId(String googleEventId) { this.googleEventId = googleEventId; return this; }
        public CalendarEventBuilder taskId(String taskId) { this.taskId = taskId; return this; }
        public CalendarEventBuilder startTime(Date startTime) { this.startTime = startTime; return this; }
        public CalendarEventBuilder endTime(Date endTime) { this.endTime = endTime; return this; }
        public CalendarEventBuilder calendarId(String calendarId) { this.calendarId = calendarId; return this; }

        public CalendarEvent build() {
            return new CalendarEvent(id, googleEventId, taskId, startTime, endTime, calendarId);
        }
    }
}
