package com.zerohour.services;

import com.zerohour.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
public class FirestoreOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

    private static final Logger log = LoggerFactory.getLogger(FirestoreOAuth2AuthorizedClientService.class);

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    @Lazy
    private ClientRegistrationRepository clientRegistrationRepository;

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, String principalName) {
        try {
            User user = firestoreService.getUser(principalName);
            if (user == null || user.getGoogleAccessToken() == null) {
                return null;
            }

            ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(clientRegistrationId);
            if (registration == null) {
                return null;
            }

            Instant now = Instant.now();
            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    user.getGoogleAccessToken(),
                    now,
                    now.plusSeconds(3600)
            );

            OAuth2RefreshToken refreshToken = null;
            if (user.getGoogleRefreshToken() != null) {
                refreshToken = new OAuth2RefreshToken(user.getGoogleRefreshToken(), now);
            }

            return (T) new OAuth2AuthorizedClient(registration, principalName, accessToken, refreshToken);
        } catch (Exception e) {
            log.warn("Unable to load authorized client for {}: {}", principalName, e.getMessage());
            return null;
        }
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        try {
            String principalName = principal.getName();
            OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
            OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

            User existing = null;
            try {
                existing = firestoreService.getUser(principalName);
            } catch (Exception e) {
                log.warn("Could not query existing user from Firestore: {}", e.getMessage());
            }

            if (existing == null) {
                existing = new User();
                existing.setUid(principalName);
                existing.setCreatedAt(new Date());
                existing.setOnboarded(false);
                
                java.util.Map<String, Object> defaultPrefs = new java.util.HashMap<>();
                defaultPrefs.put("emailNudges", true);
                defaultPrefs.put("appNudges", true);
                defaultPrefs.put("timezone", "Asia/Kolkata");
                defaultPrefs.put("twentyFourHour", true);
                defaultPrefs.put("sixHour", true);
                defaultPrefs.put("oneHour", true);
                existing.setPreferences(defaultPrefs);
            }

            if (principal instanceof OAuth2AuthenticationToken) {
                OAuth2User oauth2User = ((OAuth2AuthenticationToken) principal).getPrincipal();
                String email = (String) oauth2User.getAttributes().get("email");
                String allowedEmail = System.getenv("ALLOWED_USER_EMAIL");
                
                // Only enforce restriction if ALLOWED_USER_EMAIL is explicitly configured
                if (allowedEmail != null && !allowedEmail.trim().isEmpty() && !"*".equals(allowedEmail.trim())) {
                    if (email == null || !email.trim().equalsIgnoreCase(allowedEmail.trim())) {
                        log.warn("Access denied for email: {} (Allowed email restriction: {})", email, allowedEmail);
                        return;
                    }
                }
                
                if (email != null) {
                    existing.setEmail(email);
                }
                if (oauth2User.getAttributes().containsKey("name")) {
                    existing.setDisplayName((String) oauth2User.getAttributes().get("name"));
                }
            }

            if (accessToken != null) {
                existing.setGoogleAccessToken(accessToken.getTokenValue());
            }
            
            if (refreshToken != null && refreshToken.getTokenValue() != null) {
                existing.setGoogleRefreshToken(refreshToken.getTokenValue());
            }

            try {
                firestoreService.saveUser(existing);
                log.info("Successfully saved user credentials for {}", principalName);
            } catch (Exception e) {
                log.error("Unable to save user credentials to Firestore: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error in saveAuthorizedClient: {}", e.getMessage(), e);
        }
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        try {
            User user = firestoreService.getUser(principalName);
            if (user != null) {
                user.setGoogleAccessToken(null);
                user.setGoogleRefreshToken(null);
                firestoreService.saveUser(user);
            }
        } catch (Exception e) {
            log.warn("Error removing authorized client: {}", e.getMessage());
        }
    }
}
