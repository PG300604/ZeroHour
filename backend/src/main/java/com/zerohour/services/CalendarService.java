package com.zerohour.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerohour.models.CalendarEvent;
import com.zerohour.models.Subtask;
import com.zerohour.models.Task;
import com.zerohour.models.User;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Autowired
    private OkHttpClient client;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FirestoreService firestoreService;

    /**
     * Creates a Google Calendar event for a confirmed subtask.
     * @param userId     Firestore user UID
     * @param subtask    The confirmed Subtask POJO
     * @param parentTask The parent Task POJO (for event description)
     * @return           Google Calendar event ID (String)
     */
    public String createCalendarEvent(String userId, Subtask subtask, Task parentTask) {
        User user = firestoreService.getUserById(userId);
        if (user == null) {
            log.error("User not found: {}", userId);
            return null;
        }

        String decryptedAccessToken = user.getGoogleAccessToken();
        String calendarId = user.getGoogleCalendarId() != null && !user.getGoogleCalendarId().isEmpty() ? 
                user.getGoogleCalendarId() : "primary";

        // Calculate start time: now + sum of durations of all subtasks with orderIndex < subtask.getOrderIndex()
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        List<Subtask> allSubtasks = firestoreService.getSubtasksByTaskId(parentTask.getId());
        int totalPrevDurationMinutes = 0;
        for (Subtask st : allSubtasks) {
            if (st.getOrderIndex() < subtask.getOrderIndex()) {
                totalPrevDurationMinutes += st.getDurationMinutes();
            }
        }
        ZonedDateTime startZoned = now.plusMinutes(totalPrevDurationMinutes);
        ZonedDateTime endZoned = startZoned.plusMinutes(subtask.getDurationMinutes());

        EventDateTime startEventTime = new EventDateTime()
                .setDateTime(new DateTime(Date.from(startZoned.toInstant())))
                .setTimeZone("Asia/Kolkata");

        EventDateTime endEventTime = new EventDateTime()
                .setDateTime(new DateTime(Date.from(endZoned.toInstant())))
                .setTimeZone("Asia/Kolkata");

        Event event = new Event()
                .setSummary("[ZeroHour] " + subtask.getTitle())
                .setDescription("Auto-scheduled by ZeroHour AI • Task: " + parentTask.getTitle())
                .setStart(startEventTime)
                .setEnd(endEventTime)
                .setColorId("11"); // Red color for urgency

        try {
            Calendar service = buildCalendarClient(decryptedAccessToken);
            Event createdEvent = service.events().insert(calendarId, event).execute();
            return createdEvent.getId();
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            if (e.getStatusCode() == 401 && user.getGoogleRefreshToken() != null) {
                log.info("Access token expired for user {}, refreshing...", userId);
                String newAccessToken = refreshAccessToken(user.getGoogleRefreshToken());
                if (newAccessToken != null) {
                    // Update tokens in Firestore
                    String encryptedAccessToken = firestoreService.encrypt(newAccessToken);
                    String encryptedRefreshToken = firestoreService.encrypt(user.getGoogleRefreshToken());
                    firestoreService.updateUserTokens(userId, encryptedAccessToken, encryptedRefreshToken);
                    // Retry inserting event
                    try {
                        Calendar service = buildCalendarClient(newAccessToken);
                        Event createdEvent = service.events().insert(calendarId, event).execute();
                        return createdEvent.getId();
                    } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException ex) {
                        System.err.println("[CalendarService] Google API error: "
                            + ex.getDetails().getCode() + " — " + ex.getDetails().getMessage());
                        log.error("Retry event creation failed after token refresh", ex);
                    } catch (Exception ex) {
                        log.error("Retry event creation failed after token refresh", ex);
                    }
                }
            } else {
                System.err.println("[CalendarService] Google API error: "
                    + e.getDetails().getCode() + " — " + e.getDetails().getMessage());
                log.error("Google Calendar API error: Status Code {}", e.getStatusCode(), e);
            }
        } catch (Exception e) {
            log.error("Error creating Google Calendar Event", e);
        }
        return null;
    }

    /**
     * Deletes a Google Calendar event by event ID.
     * @param userId        Firestore user UID
     * @param googleEventId The Google Calendar event ID to delete
     */
    public void deleteCalendarEvent(String userId, String googleEventId) {
        if (googleEventId == null || googleEventId.isEmpty()) {
            return;
        }
        User user = firestoreService.getUserById(userId);
        if (user == null) {
            log.error("User not found for deleting calendar event: {}", userId);
            return;
        }

        String decryptedAccessToken = user.getGoogleAccessToken();
        String calendarId = user.getGoogleCalendarId() != null && !user.getGoogleCalendarId().isEmpty() ? 
                user.getGoogleCalendarId() : "primary";

        try {
            Calendar service = buildCalendarClient(decryptedAccessToken);
            service.events().delete(calendarId, googleEventId).execute();
            log.info("Successfully deleted Google Calendar event {}", googleEventId);
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                log.info("Google Calendar event {} already deleted (404), ignoring.", googleEventId);
            } else if (e.getStatusCode() == 401 && user.getGoogleRefreshToken() != null) {
                log.info("Access token expired for user {}, refreshing to delete event...", userId);
                String newAccessToken = refreshAccessToken(user.getGoogleRefreshToken());
                if (newAccessToken != null) {
                    String encryptedAccessToken = firestoreService.encrypt(newAccessToken);
                    String encryptedRefreshToken = firestoreService.encrypt(user.getGoogleRefreshToken());
                    firestoreService.updateUserTokens(userId, encryptedAccessToken, encryptedRefreshToken);
                    try {
                        Calendar service = buildCalendarClient(newAccessToken);
                        service.events().delete(calendarId, googleEventId).execute();
                        log.info("Successfully deleted Google Calendar event {} after token refresh", googleEventId);
                    } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException ex) {
                        if (ex.getStatusCode() == 404) {
                            log.info("Google Calendar event {} already deleted (404) after refresh, ignoring.", googleEventId);
                        } else {
                            log.error("Failed to delete event after refresh: Status Code {}", ex.getStatusCode(), ex);
                        }
                    } catch (Exception ex) {
                        log.error("Error deleting event after refresh", ex);
                    }
                }
            } else {
                log.error("Google Calendar API error deleting event: Status Code {}", e.getStatusCode(), e);
            }
        } catch (Exception e) {
            log.error("Error deleting Google Calendar event {}", googleEventId, e);
        }
    }

    /**
     * Creates calendar events for ALL confirmed subtasks of a task.
     * Packs them consecutively starting from LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
     * @param userId    Firestore user UID
     * @param taskId    The task whose subtasks to schedule
     * @return          List of CalendarEvent POJOs (with googleEventId filled in)
     */
    public List<CalendarEvent> scheduleAllSubtasks(String userId, String taskId) {
        Task parentTask = firestoreService.getTaskById(taskId);
        if (parentTask == null) {
            log.error("Task not found for scheduling: {}", taskId);
            return new ArrayList<>();
        }

        List<Subtask> subtasks = firestoreService.getSubtasksByTaskId(taskId);
        List<CalendarEvent> savedEvents = new ArrayList<>();
        ZonedDateTime startTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

        User user = firestoreService.getUserById(userId);
        String calendarId = user != null && user.getGoogleCalendarId() != null && !user.getGoogleCalendarId().isEmpty() ? 
                user.getGoogleCalendarId() : "primary";

        for (Subtask subtask : subtasks) {
            ZonedDateTime endTime = startTime.plusMinutes(subtask.getDurationMinutes());
            String googleEventId = createCalendarEvent(userId, subtask, parentTask);

            if (googleEventId != null) {
                CalendarEvent event = CalendarEvent.builder()
                        .googleEventId(googleEventId)
                        .taskId(taskId)
                        .startTime(Date.from(startTime.toInstant()))
                        .endTime(Date.from(endTime.toInstant()))
                        .calendarId(calendarId)
                        .build();

                CalendarEvent savedEvent = firestoreService.saveCalendarEvent(event);
                savedEvents.add(savedEvent);
            }
            startTime = endTime;
        }
        return savedEvents;
    }

    // PRIVATE: Token refresh helper
    private String refreshAccessToken(String refreshToken) {
        if (refreshToken == null) {
            log.error("Refresh token is null, cannot refresh access token");
            return null;
        }
        try {
            RequestBody requestBody = new FormBody.Builder()
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("refresh_token", refreshToken)
                    .add("grant_type", "refresh_token")
                    .build();

            Request request = new Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Failed to refresh Google token: {}", response.body() != null ? response.body().string() : "");
                    return null;
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                String newAccessToken = root.path("access_token").asText();
                return newAccessToken;
            }
        } catch (Exception e) {
            log.error("Error refreshing Google access token", e);
        }
        return null;
    }

    // PRIVATE: Build Calendar API client
    private Calendar buildCalendarClient(String accessToken) {
        try {
            com.google.api.client.http.HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            com.google.api.client.json.JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            
            AccessToken googleAccessToken = new AccessToken(accessToken, null);
            GoogleCredentials credentials = GoogleCredentials.create(googleAccessToken);
            HttpCredentialsAdapter requestInitializer = new HttpCredentialsAdapter(credentials);
            
            return new Calendar.Builder(httpTransport, jsonFactory, requestInitializer)
                    .setApplicationName("ZeroHour")
                    .build();
        } catch (Exception e) {
            log.error("Failed to build Calendar client", e);
            throw new RuntimeException(e);
        }
    }

    // Keep public overload for GmailService backward compatibility
    public String refreshAccessToken(User user) {
        if (user == null || user.getGoogleRefreshToken() == null) {
            return user != null ? user.getGoogleAccessToken() : null;
        }
        String newAccessToken = refreshAccessToken(user.getGoogleRefreshToken());
        if (newAccessToken != null) {
            String encryptedAccessToken = firestoreService.encrypt(newAccessToken);
            String encryptedRefreshToken = firestoreService.encrypt(user.getGoogleRefreshToken());
            firestoreService.updateUserTokens(user.getUid(), encryptedAccessToken, encryptedRefreshToken);
            user.setGoogleAccessToken(newAccessToken);
            return newAccessToken;
        }
        return user.getGoogleAccessToken();
    }

    // Keep public overload for SchedulerAgent/backward compatibility
    public java.util.Map<String, String> createCalendarEvent(User user, String title, String description, Date startTime, Date endTime) {
        if (user == null) {
            return null;
        }
        String accessToken = refreshAccessToken(user);
        if (accessToken == null) {
            return null;
        }
        String calendarId = user.getGoogleCalendarId() != null && !user.getGoogleCalendarId().isEmpty() ? 
                user.getGoogleCalendarId() : "primary";

        EventDateTime startEventTime = new EventDateTime()
                .setDateTime(new DateTime(startTime))
                .setTimeZone("Asia/Kolkata");

        EventDateTime endEventTime = new EventDateTime()
                .setDateTime(new DateTime(endTime))
                .setTimeZone("Asia/Kolkata");

        Event event = new Event()
                .setSummary("[ZeroHour] " + title)
                .setDescription(description)
                .setStart(startEventTime)
                .setEnd(endEventTime)
                .setColorId("11");

        try {
            Calendar service = buildCalendarClient(accessToken);
            Event createdEvent = service.events().insert(calendarId, event).execute();
            java.util.Map<String, String> result = new java.util.HashMap<>();
            result.put("eventId", createdEvent.getId());
            result.put("calendarId", calendarId);
            return result;
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            System.err.println("[CalendarService] Google API error: "
                + e.getDetails().getCode() + " — " + e.getDetails().getMessage());
            log.error("Error creating Google Calendar Event via legacy overload", e);
        } catch (Exception e) {
            log.error("Error creating Google Calendar Event via legacy overload", e);
        }
        return null;
    }
}
