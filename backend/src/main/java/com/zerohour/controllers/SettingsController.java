package com.zerohour.controllers;

import com.zerohour.models.User;
import com.zerohour.services.AuthService;
import com.zerohour.services.FirestoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    private AuthService authService;

    /**
     * GET /api/settings
     * Returns current user preferences for the Settings page.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getSettings() {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        User user = firestoreService.getUserById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        // Return preferences + profile info
        Map<String, Object> settings = new HashMap<>();
        settings.put("displayName", user.getDisplayName());
        settings.put("email", user.getEmail());
        settings.put("preferences", user.getPreferences() != null
            ? user.getPreferences()
            : getDefaultPreferences());
        settings.put("onboarded", user.getOnboarded() != null ? user.getOnboarded() : false);

        return ResponseEntity.ok(settings);
    }

    /**
     * PUT /api/settings/preferences
     * Updates user notification preferences.
     *
     * Body: {
     *   "emailNudges": true,
     *   "inAppNudges": true,
     *   "nudge24h": true,
     *   "nudge6h": true,
     *   "nudge1h": true,
     *   "timezone": "Asia/Kolkata"
     * }
     */
    @PutMapping("/preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @RequestBody Map<String, Object> preferences) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        // Validate preference keys — only allow known keys and validate types
        Set<String> allowedKeys = Set.of(
            "emailNudges", "inAppNudges",
            "nudge24h", "nudge6h", "nudge1h", "timezone"
        );
        Map<String, Object> sanitized = new HashMap<>();
        for (String key : allowedKeys) {
            if (preferences.containsKey(key)) {
                Object val = preferences.get(key);
                if (val == null) continue;
                if ("timezone".equals(key)) {
                    if (val instanceof String) {
                        sanitized.put(key, val);
                    } else {
                        throw new IllegalArgumentException("timezone preference must be a string");
                    }
                } else {
                    if (val instanceof Boolean) {
                        sanitized.put(key, val);
                    } else {
                        throw new IllegalArgumentException(key + " preference must be a boolean");
                    }
                }
            }
        }

        // Save to Firestore
        firestoreService.updateUserPreferences(userId, sanitized);

        return ResponseEntity.ok(sanitized);
    }

    /**
     * PUT /api/settings/onboarded
     * Marks user as having completed onboarding.
     * Called after onboarding modal is dismissed.
     */
    @PutMapping("/onboarded")
    public ResponseEntity<Map<String, Object>> markOnboarded() {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        firestoreService.markUserOnboarded(userId);
        return ResponseEntity.ok(Map.of("onboarded", true));
    }

    private Map<String, Object> getDefaultPreferences() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("emailNudges", true);
        defaults.put("inAppNudges", true);
        defaults.put("nudge24h", true);
        defaults.put("nudge6h", true);
        defaults.put("nudge1h", true);
        defaults.put("timezone", "Asia/Kolkata");
        return defaults;
    }
}
