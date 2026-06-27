package com.zerohour.services;

import com.zerohour.models.User;
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

    @Autowired
    private FirestoreService firestoreService;

    @Autowired
    @Lazy
    private ClientRegistrationRepository clientRegistrationRepository;

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId, String principalName) {
        User user = firestoreService.getUser(principalName);
        if (user == null || user.getGoogleAccessToken() == null) {
            return null;
        }

        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(clientRegistrationId);
        if (registration == null) {
            return null;
        }

        // We can set default expiration if not stored (e.g. 1 hour from now or null)
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
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        String principalName = principal.getName();
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

        User existing = firestoreService.getUser(principalName);
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
            if (oauth2User.getAttributes().containsKey("email")) {
                existing.setEmail((String) oauth2User.getAttributes().get("email"));
            }
            if (oauth2User.getAttributes().containsKey("name")) {
                existing.setDisplayName((String) oauth2User.getAttributes().get("name"));
            }
        }

        existing.setGoogleAccessToken(accessToken.getTokenValue());
        
        // Preserve old refresh token if Google didn't return a new one in this turn
        if (refreshToken != null && refreshToken.getTokenValue() != null) {
            existing.setGoogleRefreshToken(refreshToken.getTokenValue());
        }

        firestoreService.saveUser(existing);
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        User user = firestoreService.getUser(principalName);
        if (user != null) {
            user.setGoogleAccessToken(null);
            user.setGoogleRefreshToken(null);
            firestoreService.saveUser(user);
        }
    }
}
