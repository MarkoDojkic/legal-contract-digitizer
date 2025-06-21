package dev.markodojkic.legalcontractdigitizer;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
public class LegalContractDigitizerApplication {

	public static void main(String[] args) {
		Application.launch(LCDJavaFxUIApplication.class, args);
	}

}
