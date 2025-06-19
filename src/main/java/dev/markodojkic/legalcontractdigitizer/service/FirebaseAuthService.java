package dev.markodojkic.legalcontractdigitizer.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FirebaseAuthService {
	private Authentication authentication;
	private FirebaseAuth firebaseAuth;

	@PostConstruct
	public void init() {
		try {
			firebaseAuth = FirebaseAuth.getInstance();
			log.info("FirebaseAuthService initialized successfully");
		} catch (Exception e) {
			log.error("Failed to initialize FirebaseAuthService", e);
		}
	}

	public String getCurrentUserId() {
		try {
			authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication == null) {
				log.warn("No authentication found in SecurityContext");
				return null;
			}
			String token = (String) authentication.getCredentials();
			FirebaseToken decodedToken = firebaseAuth.verifyIdToken(token);
			return decodedToken.getUid();
		} catch (Exception e) {
			log.error("Failed to get current user ID from Firebase token", e);
			return null;
		}
	}
}