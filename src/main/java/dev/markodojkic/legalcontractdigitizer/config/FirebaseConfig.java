package dev.markodojkic.legalcontractdigitizer.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

import static com.google.auth.oauth2.GoogleCredentials.fromStream;

@Configuration
@Slf4j
public class FirebaseConfig {

	public FirebaseConfig() {
		try {
			// Load Firebase credentials from the service account file
			FileInputStream serviceAccount =
					new FileInputStream("/firebase-adminsdk-service-account.json");

			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(fromStream(serviceAccount))
					.build();

			// Initialize Firebase
			if (FirebaseApp.getApps().isEmpty()) {
				FirebaseApp.initializeApp(options);
			}
		} catch (IOException e) {
			log.error(e.getLocalizedMessage());
			throw new RuntimeException("Firebase connection failure, cannot start application. Check service account credentials and try starting again.");
		}
	}
}