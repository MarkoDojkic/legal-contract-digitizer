package dev.markodojkic.legalcontractdigitizer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TokenAuthService {

	public String getCurrentUserId() {
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
