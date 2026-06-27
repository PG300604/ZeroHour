package com.zerohour.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.project-id:}")
    private String projectId;

    @Value("${firebase.service-account-base64:}")
    private String serviceAccountBase64;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions.Builder builder = FirebaseOptions.builder();

            if (serviceAccountBase64 != null && !serviceAccountBase64.trim().isEmpty()) {
                // Production: decode base64 service account JSON
                byte[] decoded = Base64.getDecoder().decode(serviceAccountBase64.trim());
                InputStream serviceAccount = new ByteArrayInputStream(decoded);
                builder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
            } else {
                // Local dev: load from file path
                try {
                    FileInputStream serviceAccount =
                        new FileInputStream("src/main/resources/firebase-service-account.json");
                    builder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
                } catch (IOException e) {
                    // Fallback to application default credentials if file doesn't exist
                    builder.setCredentials(GoogleCredentials.getApplicationDefault());
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
