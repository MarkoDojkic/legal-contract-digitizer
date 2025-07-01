package dev.markodojkic.legalcontractdigitizer.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpHeaders;

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
}