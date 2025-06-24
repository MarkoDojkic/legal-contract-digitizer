package dev.markodojkic.legalcontractdigitizer.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpHeaders;

@AllArgsConstructor
public class AuthSession {
    private static String idToken;

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