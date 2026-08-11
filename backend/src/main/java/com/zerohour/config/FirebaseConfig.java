package com.zerohour.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.project-id:zerohour-a84d3}")
    private String projectId;

    @Value("${firebase.service-account-base64:}")
    private String serviceAccountBase64;

    @Bean
    public FirebaseApp firebaseApp() {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions.Builder builder = FirebaseOptions.builder();
            boolean credentialsSet = false;

            if (serviceAccountBase64 != null && !serviceAccountBase64.trim().isEmpty()) {
                String raw = serviceAccountBase64.trim();
                try {
                    byte[] jsonBytes;
                    if (raw.startsWith("{")) {
                        // Plain JSON string passed in environment
                        jsonBytes = raw.getBytes(StandardCharsets.UTF_8);
                    } else {
                        // Base64 encoded string
                        jsonBytes = Base64.getDecoder().decode(raw);
                    }
                    InputStream serviceAccount = new ByteArrayInputStream(jsonBytes);
                    builder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
                    credentialsSet = true;
                    log.info("Loaded Firebase credentials from environment variable");
                } catch (Exception e) {
                    log.error("Failed to parse FIREBASE_SERVICE_ACCOUNT_BASE64 env var: {}", e.getMessage());
                }
            }

            if (!credentialsSet) {
                // Try local dev file path
                try {
                    FileInputStream serviceAccount =
                        new FileInputStream("src/main/resources/firebase-service-account.json");
                    builder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
                    credentialsSet = true;
                    log.info("Loaded Firebase credentials from local file resource");
                } catch (Exception e) {
                    log.warn("Local firebase-service-account.json not found: {}", e.getMessage());
                }
            }

            if (!credentialsSet) {
                try {
                    builder.setCredentials(GoogleCredentials.getApplicationDefault());
                    log.info("Using GCP Application Default Credentials for Firebase");
                } catch (Exception e) {
                    log.error("Unable to load GCP Application Default Credentials: {}", e.getMessage());
                }
            }

            if (projectId != null && !projectId.trim().isEmpty()) {
                builder.setProjectId(projectId);
            }

            return FirebaseApp.initializeApp(builder.build());
        }
        return FirebaseApp.getInstance();
    }

    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore();
    }
}
