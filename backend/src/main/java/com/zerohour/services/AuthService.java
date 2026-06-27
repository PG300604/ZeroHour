package com.zerohour.services;

import com.zerohour.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private FirestoreService firestoreService;

    /**
     * Get current authenticated user's UID from Spring Security context.
     * Returns the Google OAuth sub (subject) claim as the userId.
     */
    public String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof OAuth2AuthenticationToken) {
            OAuth2User principal = ((OAuth2AuthenticationToken) auth).getPrincipal();
            if (principal != null) {
                return principal.getAttribute("sub");
            }
        }
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return null;
    }

    /**
     * Get current authenticated user's full User POJO from Firestore.
     */
    public User getCurrentUser() {
        String uid = getCurrentUserId();
        if (uid == null) {
            return null;
        }
        return firestoreService.getUserById(uid);
    }

    /**
     * Called after OAuth login success — upserts user in Firestore.
     * If user exists: update display name, email (they may have changed)
     * If user does not exist: create new User document
     */
    public void upsertUserOnLogin(OAuth2User oAuth2User) {
        if (oAuth2User == null) {
            return;
        }
        String uid = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String displayName = oAuth2User.getAttribute("name");
        if (uid != null) {
            firestoreService.upsertUser(uid, email, displayName);
        }
    }
}
