package dev.markodojkic.legalcontractdigitizer.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

/**
 * Miscellaneous configuration class for initializing Firebase connection,
 * configuring the WebClient for OpenAI API communication, and customizing
 * Jackson ObjectMapper behavior.
 *
 * <p>This configuration ensures:
 * <ul>
 *   <li>Firebase is initialized with credentials loaded from the service account JSON file.</li>
 *   <li>A WebClient bean configured with OpenAI API key and headers.</li>
 *   <li>An ObjectMapper bean customized to ignore unknown and ignored properties during deserialization
 *       and to pretty-print JSON output.</li>
 * </ul>
 */
@Configuration
@Slf4j
public class MiscellaneousConfig {

	public MiscellaneousConfig() {
		try {
			// Load Firebase credentials from the service account file
			InputStream serviceAccount = getClass().getClassLoader()
					.getResourceAsStream("firebase-adminsdk-service-account.json");

			if(serviceAccount == null) throw new IOException("Credentials file not found");
			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(fromStream(serviceAccount))
					.build();

			// Initialize Firebase
			if (FirebaseApp.getApps().isEmpty()) FirebaseApp.initializeApp(options);
		} catch (IOException e) {
			log.error("Fatal error, firebase connection failure", e);
			throw new IllegalStateException("Firebase connection failure, cannot start application. Check service account credentials and try starting again.");
		}
	}

	/**
	 * Creates a WebClient bean configured to communicate with the OpenAI API,
	 * using the API key provided via application properties.
	 *
	 * @param apiKey the OpenAI API key injected from application configuration
	 * @return configured WebClient instance
	 */
	@Bean
	public WebClient openAiWebClient(@Value("${spring.ai.openai.api-key}") String apiKey) {
		return WebClient.builder()
				.defaultHeader("Authorization", "Bearer " + apiKey)
				.defaultHeader("Content-Type", "application/json")
				.build();
	}

	/**
	 * Provides a customized Jackson ObjectMapper bean that ignores unknown and ignored
	 * properties during deserialization and enables pretty printing of JSON.
	 *
	 * @return customized ObjectMapper instance
	 */
	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper()
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
				.enable(SerializationFeature.INDENT_OUTPUT);
	}
}