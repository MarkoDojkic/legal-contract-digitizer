package dev.markodojkic.legalcontractdigitizer.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FirebaseAuthService {
	public String getCurrentUserId() {
		try {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			String token = (String) authentication.getCredentials();
			FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
			return decodedToken.getUid();
		} catch (Exception e) {
			log.error("Failed to get current user ID from Firebase token", e);
			return null;
		}
	}
}