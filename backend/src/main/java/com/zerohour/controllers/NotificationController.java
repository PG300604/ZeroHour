package com.zerohour.controllers;

import com.zerohour.models.Nudge;
import com.zerohour.services.AuthService;
import com.zerohour.services.FirestoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * NotificationController — REST API for Notification Center (F10).
 *
 * Endpoints:
 *   GET    /api/notifications              — List all sent notifications (nudges + notifications)
 *   GET    /api/notifications/unread-count  — Get count of unread notifications
 *   PUT    /api/notifications/{id}/read     — Mark single notification as read
 *   PUT    /api/notifications/read-all      — Mark all notifications as read
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    private AuthService authService;

    // ─── LIST NOTIFICATIONS ────────────────────────────────────────────────

    /**
     * GET /api/notifications — List all notifications for the current user.
     * Merges two sources:
     *   1. Nudges (from nudges collection) — sent reminders
     *   2. Notifications (from notifications collection) — agent alerts, system messages
     *
     * Returns a unified list sorted by date, newest first.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listNotifications() {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        List<Map<String, Object>> unified = new ArrayList<>();

        // 1. Get sent nudges (reminders)
        List<Nudge> allNudges = firestoreService.getInAppNudgesForUser(userId);
        for (Nudge n : allNudges) {
            if (n.isSent()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", n.getId());
                item.put("type", "NUDGE");
                item.put("nudgeType", n.getType());
                item.put("channel", n.getChannel());
                item.put("taskId", n.getTaskId());
                item.put("read", n.isRead());
                item.put("scheduledAt", n.getScheduledAt());
                item.put("title", formatNudgeTitle(n.getType()));
                item.put("body", formatNudgeBody(n));
                item.put("source", "nudge");
                unified.add(item);
            }
        }

        // 2. Get notifications from notifications collection
        List<Map<String, Object>> notifications = firestoreService.getNotificationsByUserId(userId);
        for (Map<String, Object> notif : notifications) {
            Map<String, Object> item = new HashMap<>(notif);
            item.put("source", "notification");
            if (!item.containsKey("type")) {
                item.put("type", "SYSTEM");
            }
            unified.add(item);
        }

        // Sort by date (newest first)
        unified.sort((a, b) -> {
            Date dateA = extractDate(a);
            Date dateB = extractDate(b);
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            return dateB.compareTo(dateA);
        });

        return ResponseEntity.ok(unified);
    }

    // ─── UNREAD COUNT ──────────────────────────────────────────────────────

    /**
     * GET /api/notifications/unread-count — Return count of unread notifications.
     * Used by the notification bell badge in the frontend.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount() {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        // Count unread nudges
        List<Nudge> allNudges = firestoreService.getInAppNudgesForUser(userId);
        long unreadNudges = allNudges.stream()
                .filter(n -> n.isSent() && !n.isRead())
                .count();

        // Count unread notifications from notifications collection
        int unreadNotifications = firestoreService.getUnreadNotificationCount(userId);

        Map<String, Object> resp = new HashMap<>();
        long total = unreadNudges + unreadNotifications;
        resp.put("unreadCount", total);
        resp.put("unreadNudges", unreadNudges);
        resp.put("unreadNotifications", unreadNotifications);
        resp.put("count", total);
        return ResponseEntity.ok(resp);
    }

    // ─── MARK SINGLE AS READ ───────────────────────────────────────────────

    /**
     * PUT /api/notifications/{id}/read — Mark a single notification as read.
     * Tries nudge first, then falls back to notification collection.
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Map<String, String>> markAsRead(@PathVariable String id) {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        // Try to find in nudges first
        List<Nudge> userNudges = firestoreService.getInAppNudgesForUser(userId);
        Nudge targetNudge = null;
        for (Nudge n : userNudges) {
            if (n.getId().equals(id)) {
                targetNudge = n;
                break;
            }
        }

        if (targetNudge != null) {
            targetNudge.setRead(true);
            firestoreService.updateNudge(targetNudge);
        } else {
            // Try notifications collection with ownership validation
            try {
                firestoreService.markNotificationRead(id, userId);
            } catch (Exception e) {
                return ResponseEntity.notFound().build();
            }
        }

        Map<String, String> resp = new HashMap<>();
        resp.put("status", "ok");
        return ResponseEntity.ok(resp);
    }

    // ─── MARK ALL AS READ ──────────────────────────────────────────────────

    /**
     * PUT /api/notifications/read-all — Mark all notifications as read for the current user.
     * Marks both nudges and notifications collection entries.
     */
    @PutMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllAsRead() {
        String userId = authService.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        // Mark all nudges as read
        List<Nudge> allNudges = firestoreService.getInAppNudgesForUser(userId);
        for (Nudge n : allNudges) {
            if (n.isSent() && !n.isRead()) {
                n.setRead(true);
                firestoreService.updateNudge(n);
            }
        }

        // Mark all notifications as read
        firestoreService.markAllNotificationsRead(userId);

        Map<String, String> resp = new HashMap<>();
        resp.put("status", "ok");
        return ResponseEntity.ok(resp);
    }

    // ─── HELPER METHODS ────────────────────────────────────────────────────

    private String formatNudgeTitle(String nudgeType) {
        if (nudgeType == null) return "Reminder";
        switch (nudgeType) {
            case "REMINDER_24H": return "24-Hour Reminder";
            case "REMINDER_6H": return "6-Hour Reminder";
            case "REMINDER_1H": return "1-Hour Warning";
            case "OVERDUE": return "Overdue Alert";
            default: return "Reminder";
        }
    }

    private String formatNudgeBody(Nudge nudge) {
        String typeLabel = formatNudgeTitle(nudge.getType());
        return String.format("%s for task %s", typeLabel, nudge.getTaskId());
    }

    private Date extractDate(Map<String, Object> item) {
        Object date = item.get("scheduledAt");
        if (date == null) date = item.get("createdAt");
        if (date instanceof Date) return (Date) date;
        if (date instanceof com.google.cloud.Timestamp) {
            return ((com.google.cloud.Timestamp) date).toDate();
        }
        return null;
    }
}
