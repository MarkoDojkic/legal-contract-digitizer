package dev.markodojkic.legalcontractdigitizer;

import javafx.application.Application;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.Serial;

public class LCDJavaFxUIApplication extends Application {

	private ConfigurableApplicationContext applicationContext;

	@Override
	public void init() throws Exception {
		applicationContext = new SpringApplicationBuilder(LegalContractDigitizerApplication.class).run();
	}

	@Override
	public void start(Stage stage) throws Exception {
		Font.loadFont(getClass().getResourceAsStream("/GunshipCondensedIFSCL.ttf"), 24);
		applicationContext.publishEvent(new StageReadyEvent(stage));
	}

	@Override
	public void stop() throws Exception {
		SpringApplication.exit(applicationContext);
	}

	public static class StageReadyEvent extends ApplicationEvent {
		@Serial
		private static final long serialVersionUID = 5033384426064752249L;

		public StageReadyEvent(Stage stage) {
			super(stage);
		}

		public Stage getStage() {
			return (Stage) getSource();
		}
	}

}
