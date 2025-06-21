package dev.markodojkic.legalcontractdigitizer.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthSession {
    private static String idToken;

    public static boolean isAuthenticated() {
        return idToken != null && !idToken.isEmpty();
    }

    public static void clear() {
        idToken = null;
    }
}