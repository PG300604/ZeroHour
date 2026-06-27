package com.zerohour.services;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.cloud.Timestamp;
import com.zerohour.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class FirestoreService {

    @Autowired
    private Firestore firestore;

    // Collection Names
    private static final String USERS_COLLECTION       = "users";
    private static final String TASKS_COLLECTION       = "tasks";
    private static final String SUBTASKS_COLLECTION    = "subtasks";
    private static final String PANIC_SESSIONS_COL     = "panic_sessions";
    private static final String NUDGES_COLLECTION      = "nudges";
    private static final String CALENDAR_EVENTS_COL    = "calendar_events";
    private static final String NOTIFICATIONS_COL      = "notifications";

    // --- Token Encryption / Decryption Helper Methods ---

    private static final String DEFAULT_KEY = "ZeroHourSecretEncryptionKey";

    private String getEncryptionKey() {
        String key = System.getenv("ENCRYPTION_KEY");
        if (key == null || key.isEmpty()) {
            key = System.getenv("GOOGLE_CLIENT_SECRET");
        }
        if (key == null || key.isEmpty()) {
            key = DEFAULT_KEY;
        }
        return key;
    }

    String encrypt(String value) {
        if (value == null) return null;
        try {
            byte[] keyBytes = getEncryptionKey().getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            keyBytes = sha.digest(keyBytes);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting token", e);
        }
    }

    String decrypt(String encryptedValue) {
        if (encryptedValue == null) return null;
        try {
            byte[] keyBytes = getEncryptionKey().getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            keyBytes = sha.digest(keyBytes);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedValue)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Fallback to raw text if decryption fails
            return encryptedValue;
        }
    }

    // --- POJO to Map Mappings ---

    private Map<String, Object> taskToMap(Task task) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", task.getId());
        map.put("userId", task.getUserId());
        map.put("title", task.getTitle());
        map.put("description", task.getDescription());
        map.put("status", task.getStatus());
        map.put("priority", task.getPriority());
        map.put("deadline", task.getDeadline() != null ? Timestamp.of(task.getDeadline()) : null);
        map.put("calendarEventId", task.getCalendarEventId());
        map.put("createdAt", task.getCreatedAt() != null ? Timestamp.of(task.getCreatedAt()) : Timestamp.now());
        return map;
    }

    private Map<String, Object> subtaskToMap(Subtask subtask) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", subtask.getId());
        map.put("taskId", subtask.getTaskId());
        map.put("panicSessionId", subtask.getPanicSessionId());
        map.put("title", subtask.getTitle());
        map.put("durationMinutes", subtask.getDurationMinutes());
        map.put("status", subtask.getStatus());
        map.put("orderIndex", subtask.getOrderIndex());
        map.put("priority", subtask.getPriority());
        map.put("priorityReason", subtask.getPriorityReason());
        return map;
    }

    private Map<String, Object> panicToMap(PanicSession session) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", session.getId());
        map.put("userId", session.getUserId());
        map.put("rawInput", session.getRawInput());
        map.put("conversationJson", session.getConversationJson());
        map.put("generatedPlanJson", session.getGeneratedPlanJson());
        map.put("confirmed", session.isConfirmed());
        map.put("status", session.getStatus());
        map.put("sseSessionId", session.getSseSessionId());
        map.put("createdAt", session.getCreatedAt() != null ? Timestamp.of(session.getCreatedAt()) : Timestamp.now());
        map.put("title", session.getTitle());
        return map;
    }

    private Map<String, Object> calEventToMap(CalendarEvent event) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", event.getId());
        map.put("googleEventId", event.getGoogleEventId());
        map.put("taskId", event.getTaskId());
        map.put("startTime", event.getStartTime() != null ? Timestamp.of(event.getStartTime()) : null);
        map.put("endTime", event.getEndTime() != null ? Timestamp.of(event.getEndTime()) : null);
        map.put("calendarId", event.getCalendarId());
        return map;
    }

    private Map<String, Object> nudgeToMap(Nudge nudge) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", nudge.getId());
        map.put("taskId", nudge.getTaskId());
        map.put("type", nudge.getType());
        map.put("channel", nudge.getChannel());
        map.put("scheduledAt", nudge.getScheduledAt() != null ? Timestamp.of(nudge.getScheduledAt()) : null);
        map.put("sent", nudge.isSent());
        map.put("read", nudge.isRead());
        return map;
    }

    // --- DocumentSnapshot to POJO Mappings ---

    private User documentToUser(DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        User user = new User();
        user.setUid(doc.getId());
        user.setEmail(doc.getString("email"));
        user.setDisplayName(doc.getString("displayName"));
        user.setGoogleCalendarId(doc.getString("googleCalendarId"));
        user.setGoogleAccessToken(decrypt(doc.getString("googleAccessToken")));
        user.setGoogleRefreshToken(decrypt(doc.getString("googleRefreshToken")));
        user.setPreferences((Map<String, Object>) doc.get("preferences"));
        user.setOnboarded(doc.getBoolean("onboarded"));
        
        Timestamp createdAtTs = doc.getTimestamp("createdAt");
        if (createdAtTs != null) {
            user.setCreatedAt(createdAtTs.toDate());
        }
        return user;
    }

    private Task documentToTask(DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        Task task = new Task();
        task.setId(doc.getId());
        task.setUserId(doc.getString("userId"));
        task.setTitle(doc.getString("title"));
        task.setDescription(doc.getString("description"));
        task.setStatus(doc.getString("status"));
        task.setPriority(doc.getString("priority"));
        
        Timestamp deadlineTs = doc.getTimestamp("deadline");
        if (deadlineTs != null) {
            task.setDeadline(deadlineTs.toDate());
        }
        
        task.setCalendarEventId(doc.getString("calendarEventId"));
        
        Timestamp createdAtTs = doc.getTimestamp("createdAt");
        if (createdAtTs != null) {
            task.setCreatedAt(createdAtTs.toDate());
        }
        return task;
    }

    private Subtask documentToSubtask(DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        Subtask subtask = new Subtask();
        subtask.setId(doc.getId());
        subtask.setTaskId(doc.getString("taskId"));
        subtask.setPanicSessionId(doc.getString("panicSessionId"));
        subtask.setTitle(doc.getString("title"));
        
        Long duration = doc.getLong("durationMinutes");
        subtask.setDurationMinutes(duration != null ? duration.intValue() : 0);
        
        subtask.setStatus(doc.getString("status"));
        
        Long order = doc.getLong("orderIndex");
        subtask.setOrderIndex(order != null ? order.intValue() : 0);
        
        subtask.setPriority(doc.getString("priority"));
        subtask.setPriorityReason(doc.getString("priorityReason"));
        return subtask;
    }

    private PanicSession documentToPanicSession(DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        PanicSession session = new PanicSession();
        session.setId(doc.getId());
        session.setUserId(doc.getString("userId"));
        session.setRawInput(doc.getString("rawInput"));
        session.setConversationJson(doc.getString("conversationJson"));
        session.setGeneratedPlanJson(doc.getString("generatedPlanJson"));
        session.setStatus(doc.getString("status"));
        session.setSseSessionId(doc.getString("sseSessionId"));
        session.setTitle(doc.getString("title"));
        
        Boolean confirmed = doc.getBoolean("confirmed");
        session.setConfirmed(confirmed != null && confirmed);
        
        Timestamp createdAtTs = doc.getTimestamp("createdAt");
        if (createdAtTs != null) {
            session.setCreatedAt(createdAtTs.toDate());
        }
        return session;
    }

    private CalendarEvent documentToCalendarEvent(DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        CalendarEvent event = new CalendarEvent();
        event.setId(doc.getId());
        event.setGoogleEventId(doc.getString("googleEventId"));
        event.setTaskId(doc.getString("taskId"));
        
        Timestamp startTs = doc.getTimestamp("startTime");
        if (startTs != null) {
            event.setStartTime(startTs.toDate());
        }
        
        Timestamp endTs = doc.getTimestamp("endTime");
        if (endTs != null) {
            event.setEndTime(endTs.toDate());
        }
        
        event.setCalendarId(doc.getString("calendarId"));
        return event;
    }

    private Nudge documentToNudge(DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        Nudge nudge = new Nudge();
        nudge.setId(doc.getId());
        nudge.setTaskId(doc.getString("taskId"));
        nudge.setType(doc.getString("type"));
        nudge.setChannel(doc.getString("channel"));
        
        Timestamp scheduledTs = doc.getTimestamp("scheduledAt");
        if (scheduledTs != null) {
            nudge.setScheduledAt(scheduledTs.toDate());
        }
        
        Boolean sent = doc.getBoolean("sent");
        nudge.setSent(sent != null && sent);
        
        Boolean read = doc.getBoolean("read");
        nudge.setRead(read != null && read);
        return nudge;
    }

    // --- Users ---

    public void saveUser(User user) {
        if (user == null) return;
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("uid", user.getUid());
            map.put("email", user.getEmail());
            map.put("displayName", user.getDisplayName());
            map.put("googleCalendarId", user.getGoogleCalendarId());
            map.put("googleAccessToken", encrypt(user.getGoogleAccessToken()));
            map.put("googleRefreshToken", encrypt(user.getGoogleRefreshToken()));
            map.put("preferences", user.getPreferences());
            map.put("onboarded", user.getOnboarded());
            map.put("createdAt", user.getCreatedAt() != null ? Timestamp.of(user.getCreatedAt()) : Timestamp.now());
            
            firestore.collection(USERS_COLLECTION).document(user.getUid()).set(map).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error saving user", e);
        }
    }

    public User getUser(String uid) {
        try {
            DocumentSnapshot doc = firestore.collection(USERS_COLLECTION).document(uid).get().get();
            return documentToUser(doc);
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting user", e);
        }
    }

    public User getUserById(String uid) {
        return getUser(uid);
    }

    public void upsertUser(String uid, String email, String displayName) {
        try {
            DocumentReference docRef = firestore.collection(USERS_COLLECTION).document(uid);
            DocumentSnapshot doc = docRef.get().get();
            Map<String, Object> map = new HashMap<>();
            map.put("uid", uid);
            map.put("email", email);
            map.put("displayName", displayName);
            map.put("updatedAt", Timestamp.now());
            
            if (!doc.exists()) {
                map.put("createdAt", Timestamp.now());
                map.put("onboarded", false);
                
                Map<String, Object> defaultPrefs = new HashMap<>();
                defaultPrefs.put("emailNudges", true);
                defaultPrefs.put("appNudges", true);
                defaultPrefs.put("timezone", "Asia/Kolkata");
                defaultPrefs.put("twentyFourHour", true);
                defaultPrefs.put("sixHour", true);
                defaultPrefs.put("oneHour", true);
                map.put("preferences", defaultPrefs);
            }
            docRef.set(map, SetOptions.merge()).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error upserting user", e);
        }
    }

    public void updateUserTokens(String uid, String encryptedAccessToken, String encryptedRefreshToken) {
        try {
            firestore.collection(USERS_COLLECTION).document(uid).update(
                    "googleAccessToken", encryptedAccessToken,
                    "googleRefreshToken", encryptedRefreshToken,
                    "tokenUpdatedAt", Timestamp.now()
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error updating user tokens", e);
        }
    }

    // --- Tasks ---

    public Task saveTask(Task task) {
        if (task == null) return null;
        if (task.getId() == null) {
            task.setId(UUID.randomUUID().toString());
        }
        if (task.getCreatedAt() == null) {
            task.setCreatedAt(new Date());
        }
        try {
            firestore.collection(TASKS_COLLECTION).document(task.getId()).set(taskToMap(task)).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error saving task", e);
        }
        return task;
    }

    public Task getTask(String taskId) {
        try {
            DocumentSnapshot doc = firestore.collection(TASKS_COLLECTION).document(taskId).get().get();
            return documentToTask(doc);
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting task", e);
        }
    }

    public Task getTaskById(String taskId) {
        return getTask(taskId);
    }

    public List<Task> getTasksByUserId(String userId) {
        List<Task> tasks = new ArrayList<>();
        try {
            QuerySnapshot querySnapshot = firestore.collection(TASKS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .get().get();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Task task = documentToTask(doc);
                if (task != null) {
                    tasks.add(task);
                }
            }
            // Sort in-memory: createdAt DESC
            tasks.sort((t1, t2) -> {
                Date d1 = t1.getCreatedAt();
                Date d2 = t2.getCreatedAt();
                if (d1 == null && d2 == null) return 0;
                if (d1 == null) return 1;
                if (d2 == null) return -1;
                return d2.compareTo(d1); // DESC
            });
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting tasks by user ID", e);
        }
        return tasks;
    }

    public void deleteTask(String taskId) {
        try {
            firestore.collection(TASKS_COLLECTION).document(taskId).delete().get();
            deleteSubtasksByTaskId(taskId);
            deleteNudgesByTaskId(taskId);
            deleteCalendarEventsByTaskId(taskId);
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error deleting task: " + taskId, e);
        }
    }

    public Task updateTaskStatus(String taskId, String status) {
        try {
            firestore.collection(TASKS_COLLECTION).document(taskId).update("status", status).get();
            return getTaskById(taskId);
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error updating task status", e);
        }
    }

    // --- Subtasks ---

    public void saveSubtasks(List<Subtask> subtasks) {
        saveAllSubtasks(subtasks);
    }

    public Subtask saveSubtask(Subtask subtask) {
        if (subtask == null) return null;
        if (subtask.getId() == null) {
            subtask.setId(UUID.randomUUID().toString());
        }
        try {
            firestore.collection(SUBTASKS_COLLECTION).document(subtask.getId()).set(subtaskToMap(subtask)).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error saving subtask", e);
        }
        return subtask;
    }

    public List<Subtask> saveAllSubtasks(List<Subtask> subtasks) {
        if (subtasks == null || subtasks.isEmpty()) return subtasks;
        WriteBatch batch = firestore.batch();
        for (Subtask subtask : subtasks) {
            if (subtask.getId() == null) {
                subtask.setId(UUID.randomUUID().toString());
            }
            DocumentReference ref = firestore.collection(SUBTASKS_COLLECTION).document(subtask.getId());
            batch.set(ref, subtaskToMap(subtask));
        }
        try {
            batch.commit().get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error saving all subtasks", e);
        }
        return subtasks;
    }

    public List<Subtask> getSubtasksByTaskId(String taskId) {
        List<Subtask> subtasks = new ArrayList<>();
        try {
            QuerySnapshot querySnapshot = firestore.collection(SUBTASKS_COLLECTION)
                    .whereEqualTo("taskId", taskId)
                    .get().get();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Subtask subtask = documentToSubtask(doc);
                if (subtask != null) {
                    subtasks.add(subtask);
                }
            }
            // Sort in-memory: orderIndex ASC
            subtasks.sort((s1, s2) -> Integer.compare(s1.getOrderIndex(), s2.getOrderIndex()));
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting subtasks by task ID", e);
        }
        return subtasks;
    }

    public List<Subtask> getSubtasksByPanicSessionId(String sessionId) {
        List<Subtask> subtasks = new ArrayList<>();
        try {
            QuerySnapshot querySnapshot = firestore.collection(SUBTASKS_COLLECTION)
                    .whereEqualTo("panicSessionId", sessionId)
                    .get().get();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Subtask subtask = documentToSubtask(doc);
                if (subtask != null) {
                    subtasks.add(subtask);
                }
            }
            // Sort in-memory: orderIndex ASC
            subtasks.sort((s1, s2) -> Integer.compare(s1.getOrderIndex(), s2.getOrderIndex()));
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting subtasks by panic session ID", e);
        }
        return subtasks;
    }

    public void deleteSubtasksByTaskId(String taskId) {
        try {
            QuerySnapshot querySnapshot = firestore.collection(SUBTASKS_COLLECTION)
                    .whereEqualTo("taskId", taskId)
                    .get().get();
            if (querySnapshot.isEmpty()) return;

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                batch.delete(doc.getReference());
            }
            batch.commit().get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error deleting subtasks for task ID: " + taskId, e);
        }
    }

    public Subtask updateSubtaskStatus(String subtaskId, String status) {
        try {
            firestore.collection(SUBTASKS_COLLECTION).document(subtaskId).update("status", status).get();
            DocumentSnapshot doc = firestore.collection(SUBTASKS_COLLECTION).document(subtaskId).get().get();
            return documentToSubtask(doc);
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error updating subtask status", e);
        }
    }

    // --- PanicSessions ---

    public PanicSession savePanicSession(PanicSession session) {
        if (session == null) return null;
        if (session.getId() == null) {
            session.setId(UUID.randomUUID().toString());
        }
        if (session.getCreatedAt() == null) {
            session.setCreatedAt(new Date());
        }
        try {
            firestore.collection(PANIC_SESSIONS_COL).document(session.getId()).set(panicToMap(session)).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error saving panic session", e);
        }
        return session;
    }

    public void deletePanicSession(String id) {
        try {
            firestore.collection(PANIC_SESSIONS_COL).document(id).delete().get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error deleting panic session", e);
        }
    }

    public PanicSession getPanicSession(String id) {
        try {
            DocumentSnapshot doc = firestore.collection(PANIC_SESSIONS_COL).document(id).get().get();
            return documentToPanicSession(doc);
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting panic session", e);
        }
    }

    public PanicSession getPanicSessionById(String sessionId) {
        return getPanicSession(sessionId);
    }

    public PanicSession updatePanicSession(PanicSession session) {
        return savePanicSession(session);
    }

    public List<PanicSession> getPanicSessionsByUserId(String userId) {
        List<PanicSession> sessions = new ArrayList<>();
        try {
            QuerySnapshot querySnapshot = firestore.collection(PANIC_SESSIONS_COL)
                    .whereEqualTo("userId", userId)
                    .get().get();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                PanicSession session = documentToPanicSession(doc);
                if (session != null) {
                    sessions.add(session);
                }
            }
            // Sort in memory by createdAt descending to avoid requiring composite index
            sessions.sort((a, b) -> {
                java.util.Date ca = a.getCreatedAt();
                java.util.Date cb = b.getCreatedAt();
                if (ca == null) return 1;
                if (cb == null) return -1;
                return cb.compareTo(ca);
            });
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting panic sessions by user ID", e);
        }
        return sessions;
    }

    public Subtask getSubtaskById(String subtaskId) {
        try {
            DocumentSnapshot doc = firestore.collection(SUBTASKS_COLLECTION).document(subtaskId).get().get();
            if (!doc.exists()) return null;
            return documentToSubtask(doc);
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting subtask by ID", e);
        }
    }

    public int getUnreadNotificationCount(String userId) {
        try {
            QuerySnapshot querySnapshot = firestore.collection(NOTIFICATIONS_COL)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("read", false)
                    .get().get();
            return querySnapshot.size();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting unread notification count", e);
        }
    }

    // --- CalendarEvents ---

    public CalendarEvent saveCalendarEvent(CalendarEvent event) {
        if (event == null) return null;
        if (event.getId() == null) {
            event.setId(UUID.randomUUID().toString());
        }
        try {
            firestore.collection(CALENDAR_EVENTS_COL).document(event.getId()).set(calEventToMap(event)).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error saving calendar event", e);
        }
        return event;
    }

    public List<CalendarEvent> getCalendarEventsByTaskId(String taskId) {
        List<CalendarEvent> events = new ArrayList<>();
        try {
            QuerySnapshot querySnapshot = firestore.collection(CALENDAR_EVENTS_COL)
                    .whereEqualTo("taskId", taskId)
                    .get().get();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                CalendarEvent event = documentToCalendarEvent(doc);
                if (event != null) {
                    events.add(event);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting calendar events by task ID", e);
        }
        return events;
    }

    public void deleteCalendarEventsByTaskId(String taskId) {
        try {
            QuerySnapshot querySnapshot = firestore.collection(CALENDAR_EVENTS_COL)
                    .whereEqualTo("taskId", taskId)
                    .get().get();
            if (querySnapshot.isEmpty()) return;

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                batch.delete(doc.getReference());
            }
            batch.commit().get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error deleting calendar events for task ID: " + taskId, e);
        }
    }

    // --- Nudges ---

    public void saveNudges(List<Nudge> nudges) {
        if (nudges == null || nudges.isEmpty()) return;
        WriteBatch batch = firestore.batch();
        for (Nudge nudge : nudges) {
            if (nudge.getId() == null) {
                nudge.setId(UUID.randomUUID().toString());
            }
            DocumentReference ref = firestore.collection(NUDGES_COLLECTION).document(nudge.getId());
            batch.set(ref, nudgeToMap(nudge));
        }
        try {
            batch.commit().get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error saving nudges", e);
        }
    }

    public Nudge saveNudge(Nudge nudge) {
        if (nudge == null) return null;
        if (nudge.getId() == null) {
            nudge.setId(UUID.randomUUID().toString());
        }
        try {
            firestore.collection(NUDGES_COLLECTION).document(nudge.getId()).set(nudgeToMap(nudge)).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error saving nudge", e);
        }
        return nudge;
    }

    public void updateNudge(Nudge nudge) {
        saveNudge(nudge);
    }

    public List<Nudge> getNudgesByTaskId(String taskId) {
        List<Nudge> nudges = new ArrayList<>();
        try {
            QuerySnapshot querySnapshot = firestore.collection(NUDGES_COLLECTION)
                    .whereEqualTo("taskId", taskId)
                    .get().get();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Nudge nudge = documentToNudge(doc);
                if (nudge != null) {
                    nudges.add(nudge);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting nudges by task ID", e);
        }
        return nudges;
    }

    public void deleteNudgesByTaskId(String taskId) {
        try {
            QuerySnapshot querySnapshot = firestore.collection(NUDGES_COLLECTION)
                    .whereEqualTo("taskId", taskId)
                    .get().get();
            if (querySnapshot.isEmpty()) return;

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                batch.delete(doc.getReference());
            }
            batch.commit().get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error deleting nudges for task ID: " + taskId, e);
        }
    }

    public List<Nudge> getPendingNudges() {
        List<Nudge> nudges = new ArrayList<>();
        try {
            QuerySnapshot querySnapshot = firestore.collection(NUDGES_COLLECTION)
                    .whereEqualTo("sent", false)
                    .get().get();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Nudge nudge = documentToNudge(doc);
                if (nudge != null) {
                    nudges.add(nudge);
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting pending nudges", e);
        }
        return nudges;
    }

    public List<Nudge> getInAppNudgesForUser(String userId) {
        List<Nudge> nudges = new ArrayList<>();
        try {
            List<Task> tasks = getTasksByUserId(userId);
            if (tasks.isEmpty()) return nudges;

            List<String> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toList());
            for (int i = 0; i < taskIds.size(); i += 30) {
                List<String> subList = taskIds.subList(i, Math.min(i + 30, taskIds.size()));
                QuerySnapshot querySnapshot = firestore.collection(NUDGES_COLLECTION)
                        .whereIn("taskId", subList)
                        .get().get();
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Nudge nudge = documentToNudge(doc);
                    if (nudge != null) {
                        nudges.add(nudge);
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting in-app nudges for user", e);
        }
        return nudges;
    }

    public List<Nudge> getUnsentNudgesBefore(Timestamp cutoff) {
        List<Nudge> nudges = new ArrayList<>();
        try {
            QuerySnapshot querySnapshot = firestore.collection(NUDGES_COLLECTION)
                    .whereEqualTo("sent", false)
                    .get().get();
            Date cutoffDate = cutoff.toDate();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Nudge nudge = documentToNudge(doc);
                if (nudge != null) {
                    if (nudge.getScheduledAt() != null && !nudge.getScheduledAt().after(cutoffDate)) {
                        nudges.add(nudge);
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting unsent nudges before cutoff", e);
        }
        return nudges;
    }

    public void markNudgeAsSent(String nudgeId) {
        try {
            firestore.collection(NUDGES_COLLECTION).document(nudgeId).update("sent", true).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error marking nudge as sent", e);
        }
    }

    // --- Notifications ---

    public List<Map<String, Object>> getNotificationsByUserId(String userId) {
        List<Map<String, Object>> notifications = new ArrayList<>();
        try {
            QuerySnapshot querySnapshot = firestore.collection(NOTIFICATIONS_COL)
                    .whereEqualTo("userId", userId)
                    .get().get();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                Map<String, Object> data = doc.getData();
                if (data != null) {
                    data.put("id", doc.getId());
                    notifications.add(data);
                }
            }
            // Sort in-memory: createdAt DESC
            notifications.sort((n1, n2) -> {
                Object c1 = n1.get("createdAt");
                Object c2 = n2.get("createdAt");
                Timestamp t1 = c1 instanceof Timestamp ? (Timestamp) c1 : null;
                Timestamp t2 = c2 instanceof Timestamp ? (Timestamp) c2 : null;
                if (t1 == null && t2 == null) return 0;
                if (t1 == null) return 1;
                if (t2 == null) return -1;
                return t2.compareTo(t1); // DESC
            });
            // Limit to 20
            if (notifications.size() > 20) {
                notifications = new ArrayList<>(notifications.subList(0, 20));
            }
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error getting notifications by user ID", e);
        }
        return notifications;
    }

    public void markNotificationRead(String notificationId, String userId) {
        try {
            DocumentSnapshot doc = firestore.collection(NOTIFICATIONS_COL).document(notificationId).get().get();
            if (doc.exists() && userId.equals(doc.getString("userId"))) {
                doc.getReference().update("read", true).get();
            } else {
                throw new RuntimeException("Notification not found or access denied");
            }
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error marking notification as read", e);
        }
    }

    public void markAllNotificationsRead(String userId) {
        try {
            QuerySnapshot querySnapshot = firestore.collection(NOTIFICATIONS_COL)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("read", false)
                    .get().get();
            if (querySnapshot.isEmpty()) return;

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                batch.update(doc.getReference(), "read", true);
            }
            batch.commit().get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error marking all notifications read", e);
        }
    }

    public void saveNotification(String userId, String type, String title, String body, String taskId) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("type", type);
        map.put("title", title);
        map.put("body", body);
        map.put("taskId", taskId);
        map.put("read", false);
        map.put("createdAt", Timestamp.now());

        String id = UUID.randomUUID().toString();
        try {
            firestore.collection(NOTIFICATIONS_COL).document(id).set(map).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error saving notification", e);
        }
    }

    public void updateUserPreferences(String userId, Map<String, Object> preferences) {
        try {
            firestore.collection(USERS_COLLECTION).document(userId).update("preferences", preferences).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error updating user preferences", e);
        }
    }

    public void markUserOnboarded(String userId) {
        try {
            firestore.collection(USERS_COLLECTION).document(userId).update("onboarded", true).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error marking user onboarded", e);
        }
    }
}
