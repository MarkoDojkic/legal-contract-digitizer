package dev.markodojkic.legalcontractdigitizer.util;

import org.springframework.http.HttpHeaders;

public class AuthSession {
    private static String idToken;

    private AuthSession() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    public static boolean isAuthenticated() {
        return idToken != null && !idToken.isEmpty();
    }

    public static void setIdToken(String idToken) {
        AuthSession.idToken = idToken;
    }

    public static HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (isAuthenticated()) {
            headers.setBearerAuth(idToken);
        }
        return headers;
    }
}