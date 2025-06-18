package dev.markodojkic.legalcontractdigitizer.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class FirebaseAuthService {

	public String getCurrentUserId() {
		try {
			// Get the current user's Firebase JWT token
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			String token = (String) authentication.getCredentials();
			FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
			return decodedToken.getUid();  // User ID from Firebase
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}