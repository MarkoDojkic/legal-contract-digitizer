package dev.markodojkic.legalcontractdigitizer.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Value("${google.client.id}")
    private String clientId;

    @PostMapping("/google")
    public ResponseEntity<?> authenticateWithGoogle(@RequestBody Map<String, String> body) {
        String idTokenString = body.get("idToken");

        if (idTokenString == null) {
            return ResponseEntity.badRequest().body("Missing idToken");
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier
                    .Builder(new NetHttpTransport(), new JacksonFactory())
                    .setAudience(Collections.singletonList(clientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String userId = payload.getSubject(); // Use this as UID
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            // Optionally, create or fetch user record in your DB / Firestore here

            // Return something meaningful (e.g., app token or success message)
            return ResponseEntity.ok(Map.of(
                    "userId", userId,
                    "email", email,
                    "name", name
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token verification failed: " + e.getMessage());
        }
    }
}