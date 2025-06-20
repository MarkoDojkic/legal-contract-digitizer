package dev.markodojkic.legalcontractdigitizer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;

import static com.google.auth.oauth2.GoogleCredentials.fromStream;

@Configuration
@Slf4j
public class MiscellaneousConfig {

	public MiscellaneousConfig() {
		try {
			// Load Firebase credentials from the service account file
			InputStream serviceAccount = getClass().getClassLoader()
					.getResourceAsStream("firebase-adminsdk-service-account.json");

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

	@Bean
	public WebClient openAiWebClient(@Value("${spring.ai.openai.api-key}") String apiKey) {
		return WebClient.builder()
				.defaultHeader("Authorization", "Bearer " + apiKey)
				.defaultHeader("Content-Type", "application/json")
				.build();
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}