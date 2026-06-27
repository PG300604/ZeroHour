package com.zerohour.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerohour.models.User;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class GmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailService.class);

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private OkHttpClient client;

    @Autowired
    private ObjectMapper objectMapper;

    public boolean sendNudgeEmail(User user, String subject, String messageText) {
        if (user != null && user.getPreferences() != null) {
            // Check global email nudges toggle
            Object emailPref = user.getPreferences().get("emailNudges");
            if (emailPref instanceof Boolean && !((Boolean) emailPref)) {
                log.info("Email nudges are globally disabled for user: {}. Skipping email.", user.getEmail());
                return true; // Return true so it's marked as successfully processed
            }

            // Check individual threshold toggles (24h, 6h, 1h)
            if (subject != null) {
                if (subject.contains("24 hours")) {
                    Object p24 = user.getPreferences().get("twentyFourHour");
                    if (p24 == null) p24 = user.getPreferences().get("threshold24h");
                    if (p24 instanceof Boolean && !((Boolean) p24)) {
                        log.info("24h reminder is disabled for user: {}. Skipping email.", user.getEmail());
                        return true;
                    }
                } else if (subject.contains("6 hours")) {
                    Object p6 = user.getPreferences().get("sixHour");
                    if (p6 == null) p6 = user.getPreferences().get("threshold6h");
                    if (p6 instanceof Boolean && !((Boolean) p6)) {
                        log.info("6h reminder is disabled for user: {}. Skipping email.", user.getEmail());
                        return true;
                    }
                } else if (subject.contains("1 hour")) {
                    Object p1 = user.getPreferences().get("oneHour");
                    if (p1 == null) p1 = user.getPreferences().get("threshold1h");
                    if (p1 instanceof Boolean && !((Boolean) p1)) {
                        log.info("1h reminder is disabled for user: {}. Skipping email.", user.getEmail());
                        return true;
                    }
                }
            }
        }

        String accessToken = calendarService.refreshAccessToken(user);
        if (accessToken == null) {
            log.error("Cannot send email: failed to refresh/get access token.");
            return false;
        }

        log.info("Sending nudge email to user: {}", user.getEmail());

        try {
            // Build standard MIME RFC 822 email format
            String rawEmailContent = String.format(
                    "From: me\r\n" +
                    "To: %s\r\n" +
                    "Subject: %s\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n\r\n" +
                    "<html><body>%s</body></html>",
                    user.getEmail(),
                    subject,
                    messageText.replace("\n", "<br>")
            );

            // Encode using URL-safe Base64 without padding
            String encodedRaw = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(rawEmailContent.getBytes(StandardCharsets.UTF_8));

            Map<String, String> bodyMap = new HashMap<>();
            bodyMap.put("raw", encodedRaw);

            String requestBodyJson = objectMapper.writeValueAsString(bodyMap);
            RequestBody body = RequestBody.create(requestBodyJson, MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url("https://www.googleapis.com/gmail/v1/users/me/messages/send")
                    .post(body)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respStr = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.error("Google Gmail API call failed: HTTP {} - {}", response.code(), respStr);
                    return false;
                }
                log.info("Email sent successfully via Gmail API!");
                return true;
            }
        } catch (Exception e) {
            log.error("Error calling Gmail API", e);
        }
        return false;
    }
}
