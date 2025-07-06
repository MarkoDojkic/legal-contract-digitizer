package dev.markodojkic.legalcontractdigitizer.util;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility class for managing authentication session data,
 * such as access and refresh tokens, and extracting user identity
 * from the Spring Security context.
 */
@Slf4j
public final class AuthSession {

    @Getter
    @Setter
    private static String accessToken, refreshToken;

    /** Prevents instantiation of utility class */
    private AuthSession() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Checks whether a valid access token is currently set.
     *
     * @return true if access token is non-null and non-empty
     */
    public static boolean hasAccessToken() {
        return accessToken != null && !accessToken.isEmpty();
    }

    /**
     * Creates HTTP headers with the current bearer access token.
     *
     * @return HttpHeaders containing Authorization header
     */
    public static HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    /**
     * Checks whether a valid refresh token is currently set.
     *
     * @return true if refresh token is non-null and non-empty
     */
    public static boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isEmpty();
    }

    /**
     * Clears the current access and refresh tokens.
     */
    public static void logout() {
        accessToken = null;
        refreshToken = null;
    }

    /**
     * Retrieves the user ID from the Spring Security context.
     *
     * @return user ID as String, or null if not authenticated or invalid
     */
    public static String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null) {
                log.warn("No authentication found in SecurityContext");
                return null;
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof String userId) {
                return userId;
            } else {
                log.warn("Principal is not a String, got: {}", principal.getClass());
                return null;
            }

        } catch (Exception e) {
            log.error("Error retrieving current user ID", e);
            return null;
        }
    }
}