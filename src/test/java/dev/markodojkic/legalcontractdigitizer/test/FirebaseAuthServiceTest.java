package dev.markodojkic.legalcontractdigitizer.test;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import dev.markodojkic.legalcontractdigitizer.service.FirebaseAuthService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FirebaseAuthServiceTest {

	@Test
	void getCurrentUserId_shouldReturnUid() throws Exception {
		FirebaseAuthService service = new FirebaseAuthService();

		Authentication auth = mock(Authentication.class);
		when(auth.getCredentials()).thenReturn("mockToken");

		SecurityContext context = mock(SecurityContext.class);
		when(context.getAuthentication()).thenReturn(auth);

		try (MockedStatic<SecurityContextHolder> sch = mockStatic(SecurityContextHolder.class);
		     MockedStatic<FirebaseAuth> authStatic = mockStatic(FirebaseAuth.class)) {

			sch.when(SecurityContextHolder::getContext).thenReturn(context);

			FirebaseToken token = mock(FirebaseToken.class);
			when(token.getUid()).thenReturn("user123");

			FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
			when(firebaseAuth.verifyIdToken("mockToken")).thenReturn(token);

			authStatic.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);

			String uid = service.getCurrentUserId();
			assertEquals("user123", uid);
		}
	}
}