package dev.markodojkic.legalcontractdigitizer.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

	public FirebaseConfig() {
		try {
			// Load Firebase credentials from the service account file
			FileInputStream serviceAccount =
					new FileInputStream("/firebase-adminsdk-service-account.json");

			FirebaseOptions options = new FirebaseOptions.Builder()
					.setCredentials(com.google.auth.oauth2.GoogleCredentials.fromStream(serviceAccount))
					.build();

			// Initialize Firebase
			FirebaseApp.initializeApp(options);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}