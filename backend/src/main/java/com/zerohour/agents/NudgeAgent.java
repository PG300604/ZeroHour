package com.zerohour.agents;

import com.google.cloud.Timestamp;
import com.zerohour.models.Nudge;
import com.zerohour.models.Task;
import com.zerohour.models.User;
import com.zerohour.services.FirestoreService;
import com.zerohour.services.GmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * NudgeAgent — Schedules and sends reminder nudges (24h, 6h, 1h before deadline).
 *
 * Day 4 hardening:
 * - Structured cron logging for easy demo verification
 * - Per-nudge error isolation
 * - Tick-level logging (even when nothing fires)
 */
@Service
public class NudgeAgent {

    private static final Logger log = LoggerFactory.getLogger(NudgeAgent.class);

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    private GmailService gmailService;

    @org.springframework.context.annotation.Lazy
    @Autowired
    private AgentOrchestrator agentOrchestrator;

    // ─── ORCHESTRATOR API ──────────────────────────────────────────────────

    public int scheduleNudgesForTask(String taskId, String sessionId) {
        try {
            Task task = firestoreService.getTask(taskId);
            if (task == null || task.getDeadline() == null) {
                log.info("NudgeAgent: No task or deadline for taskId {}", taskId);
                return 0;
            }

            List<Nudge> nudges = createNudgesForDeadline(taskId, task.getDeadline());

            if (!nudges.isEmpty()) {
                firestoreService.saveNudges(nudges);
            }

            return nudges.size();

        } catch (Exception e) {
            log.error("NudgeAgent error scheduling nudges for task {}: {}", taskId, e.getMessage(), e);
            return 0;
        }
    }

    // ─── BACKWARD-COMPATIBLE API ───────────────────────────────────────────

    public void preScheduleNudges(String sessionId, Task task) {
        log.info("NudgeAgent: Pre-scheduling nudges for session {} / task {}", sessionId, task.getId());
        agentOrchestrator.logProgress(sessionId, "NudgeAgent", "THINKING", "Scheduling reminders...");

        if (task.getDeadline() == null) {
            agentOrchestrator.logProgress(sessionId, "NudgeAgent", "DONE", "No deadline set, skipped scheduling reminders.");
            return;
        }

        List<Nudge> nudges = createNudgesForDeadline(task.getId(), task.getDeadline());

        if (!nudges.isEmpty()) {
            firestoreService.saveNudges(nudges);
            List<String> activeReminders = new ArrayList<>();
            for (Nudge n : nudges) {
                if ("REMINDER_24H".equals(n.getType())) activeReminders.add("24h");
                else if ("REMINDER_6H".equals(n.getType())) activeReminders.add("6h");
                else if ("REMINDER_1H".equals(n.getType())) activeReminders.add("1h");
            }
            String summary = String.join(" and ", activeReminders) + " before deadline";
            agentOrchestrator.logProgress(sessionId, "NudgeAgent", "DONE", "Reminders set for " + summary + ".");
        } else {
            agentOrchestrator.logProgress(sessionId, "NudgeAgent", "DONE", "Task deadline is too close for pre-scheduled nudges.");
        }
    }

    // ─── CRON JOB: PROCESS SCHEDULED NUDGES ────────────────────────────────

    /**
     * Runs every 15 minutes.
     * Scans Firestore for unsent nudges whose scheduledAt <= now.
     * Sends in-app notification + email for each.
     * Marks nudge as sent to prevent duplicates.
     *
     * Day 4: Added structured logging for demo verification.
     */
    @Scheduled(fixedRate = 900_000)
    @Async("sseTaskExecutor")
    public void processScheduledNudges() {
        LocalDateTime tick = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        System.out.println("[NudgeAgent] ⏱ Cron tick at " + tick + " (Asia/Kolkata)");

        try {
            Timestamp now = Timestamp.now();
            List<Nudge> dueNudges = firestoreService.getUnsentNudgesBefore(now);

            System.out.println("[NudgeAgent] Found " + dueNudges.size() + " due nudges");

            if (dueNudges.isEmpty()) return;

            int successCount = 0;
            int failCount = 0;

            for (Nudge nudge : dueNudges) {
                try {
                    processNudge(nudge);
                    successCount++;
                    System.out.println("[NudgeAgent] ✓ Processed nudge "
                            + nudge.getId() + " type=" + nudge.getType());
                } catch (Exception e) {
                    failCount++;
                    System.err.println("[NudgeAgent] ✗ Failed nudge "
                            + nudge.getId() + ": " + e.getMessage());
                    log.error("NudgeAgent: Failed to process nudge {} — {}", nudge.getId(), e.getMessage(), e);
                }
            }

            System.out.println("[NudgeAgent] Cron complete — processed "
                    + successCount + "/" + dueNudges.size()
                    + " (" + failCount + " failed)");

        } catch (Exception e) {
            System.err.println("[NudgeAgent] Cron error: " + e.getMessage());
            log.error("NudgeAgent cron error: {}", e.getMessage(), e);
        }
    }

    /**
     * Process a single nudge:
     * 1. Get the task it belongs to
     * 2. Get the user who owns the task
     * 3. Send in-app notification (write nudge read=false for frontend polling)
     * 4. Send email if channel is EMAIL or BOTH (GmailService checks preferences)
     * 5. Mark nudge as sent
     */
    private void processNudge(Nudge nudge) {
        Task task = firestoreService.getTaskById(nudge.getTaskId());
        if (task == null) {
            log.info("NudgeAgent: Task {} not found for nudge {}, marking as sent", nudge.getTaskId(), nudge.getId());
            firestoreService.markNudgeAsSent(nudge.getId());
            return;
        }

        User user = firestoreService.getUserById(task.getUserId());
        if (user == null) {
            log.warn("NudgeAgent: User {} not found for task {}, marking nudge as sent", task.getUserId(), task.getId());
            firestoreService.markNudgeAsSent(nudge.getId());
            return;
        }

        String timeLabel = getTimeLabelFromType(nudge.getType());

        // In-app notification
        if ("IN_APP".equals(nudge.getChannel()) || "BOTH".equals(nudge.getChannel())) {
            saveInAppNotification(task, user.getUid(), timeLabel);
        }

        // Email notification
        if ("EMAIL".equals(nudge.getChannel()) || "BOTH".equals(nudge.getChannel())) {
            String subject = buildEmailSubject(task.getTitle(), nudge.getType());
            String body = buildEmailBody(task, user, timeLabel);
            boolean emailSent = gmailService.sendNudgeEmail(user, subject, body);
            if (!emailSent) {
                log.warn("NudgeAgent: Email send failed for nudge {} (task: {}), but marking as sent to avoid retry spam",
                        nudge.getId(), task.getTitle());
            }
        }

        // Mark as sent — critical: prevents duplicate sends
        firestoreService.markNudgeAsSent(nudge.getId());
        log.info("NudgeAgent: Sent nudge for task [{}] → {}", task.getTitle(), nudge.getType());
    }

    /**
     * Manually trigger cron for edge cases (deadline very soon after confirm).
     */
    public void checkImmediately() {
        processScheduledNudges();
    }

    // ─── IN-APP NOTIFICATION ──────────────────────────────────────────────

    private void saveInAppNotification(Task task, String userId, String timeLabel) {
        try {
            Nudge inAppNudge = Nudge.builder()
                    .id(UUID.randomUUID().toString())
                    .taskId(task.getId())
                    .type("IN_APP_NOTIFICATION")
                    .channel("IN_APP")
                    .scheduledAt(new Date())
                    .sent(true)
                    .read(false)
                    .build();
            firestoreService.saveNudge(inAppNudge);
            log.debug("NudgeAgent: Saved in-app notification for task [{}] to user {}", task.getTitle(), userId);
        } catch (Exception e) {
            log.error("NudgeAgent: Failed to save in-app notification for task {}: {}", task.getId(), e.getMessage());
        }
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────

    private List<Nudge> createNudgesForDeadline(String taskId, Date deadline) {
        List<Nudge> nudges = new ArrayList<>();
        long deadlineTime = deadline.getTime();
        long now = System.currentTimeMillis();

        long oneHour = 60 * 60 * 1000L;
        long sixHours = 6 * oneHour;
        long twentyFourHours = 24 * oneHour;

        long t24 = deadlineTime - twentyFourHours;
        if (t24 > now) {
            nudges.add(Nudge.builder()
                    .taskId(taskId).type("REMINDER_24H").channel("BOTH")
                    .scheduledAt(new Date(t24)).sent(false).build());
        }

        long t6 = deadlineTime - sixHours;
        if (t6 > now) {
            nudges.add(Nudge.builder()
                    .taskId(taskId).type("REMINDER_6H").channel("BOTH")
                    .scheduledAt(new Date(t6)).sent(false).build());
        }

        long t1 = deadlineTime - oneHour;
        if (t1 > now) {
            nudges.add(Nudge.builder()
                    .taskId(taskId).type("REMINDER_1H").channel("BOTH")
                    .scheduledAt(new Date(t1)).sent(false).build());
        }

        return nudges;
    }

    private String getTimeLabelFromType(String nudgeType) {
        return switch (nudgeType) {
            case "REMINDER_24H" -> "24 hours";
            case "REMINDER_6H"  -> "6 hours";
            case "REMINDER_1H"  -> "1 hour";
            default             -> "soon";
        };
    }

    private String getFriendlyTimeLabel(String type) {
        return getTimeLabelFromType(type);
    }

    private String buildEmailSubject(String taskTitle, String nudgeType) {
        String timeLabel = getTimeLabelFromType(nudgeType);
        return String.format("ZeroHour: \"%s\" is due in %s", taskTitle, timeLabel);
    }

    private String buildEmailBody(Task task, User user, String timeLabel) {
        return String.format(
                "<h3>ZeroHour — Your Last-Minute Life Saver</h3>" +
                "<p>Hi %s,</p>" +
                "<p>ZeroHour is reminding you about an upcoming deadline.</p>" +
                "<p><strong>Task:</strong> %s</p>" +
                "<p><strong>Due in:</strong> %s</p>" +
                "<p><strong>Priority:</strong> %s</p>" +
                "<p><strong>Deadline:</strong> %s</p>" +
                "<br>" +
                "<p>Don't let this one slip. Open ZeroHour to see your plan and take action.</p>" +
                "<br>" +
                "<p>Best,<br>ZeroHour AI · Your last-minute life saver</p>" +
                "<p style=\"color: #888; font-size: 12px;\">To manage notification preferences, visit Settings in ZeroHour.</p>",
                user.getDisplayName() != null ? user.getDisplayName() : "there",
                task.getTitle(),
                timeLabel,
                task.getPriority() != null ? task.getPriority() : "MEDIUM",
                task.getDeadline() != null ? task.getDeadline().toString() : "Not set"
        );
    }
}
