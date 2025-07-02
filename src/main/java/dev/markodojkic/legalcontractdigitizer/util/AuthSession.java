package dev.markodojkic.legalcontractdigitizer.util;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public class AuthSession {
    @Getter
    @Setter
    private static String accessToken, refreshToken;

    private AuthSession() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    public static boolean hasAccessToken() {
        return accessToken != null && !accessToken.isEmpty();
    }

    public static HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    public static void logout() {
        accessToken = null;
        refreshToken = null;
    }

    public static String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                log.warn("No authentication found in SecurityContext");
                return null;
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof String userId) return userId;
             else {
                log.warn("Principal is not a String, got: {}", principal.getClass());
                return null;
            }
        } catch (Exception e) {
            log.error("Error retrieving current user ID", e);
            return null;
        }
    }
}