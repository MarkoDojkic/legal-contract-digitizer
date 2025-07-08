package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.model.DigitalizedContract;
import dev.markodojkic.legalcontractdigitizer.util.HttpClientUtil;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SolidityViewController extends WindowAwareController {
	@Setter
	private String text;

	@Setter
	@FXML private Button mainRefreshBtn;
	@FXML private TextArea textArea;

	@Setter
	private String contractId;

	private final HttpClientUtil httpClientUtil;
	private final String baseUrl;

	@Autowired
	public SolidityViewController(@Value("${server.port}") Integer serverPort, WindowLauncher windowLauncher, ApplicationContext applicationContext, HttpClientUtil httpClientUtil){
		super(windowLauncher, applicationContext);
		this.baseUrl = String.format("http://localhost:%s/api/v1/contracts", serverPort);
		this.httpClientUtil = httpClientUtil;
	}

	@FXML
	private void initialize() {
		Platform.runLater(() -> {
			textArea.setText(text);
			textArea.setEditable(false);
			textArea.setFocusTraversable(false);

			if (contractId != null) {
				final ToggleButton editToggle = new ToggleButton("📝");
				final Button editToggleHelpButton = new Button("?");
				editToggleHelpButton.setOnAction(_ -> windowLauncher.launchHelpSpecialWindow("Button left from this help button is toggle button\nWhen on Solidity code is on edit mode, after switching off it gets replaced in database (background blinks green twice if change is successful)"));
				editToggleHelpButton.getStyleClass().add("btn-help");
				editToggleHelpButton.setPrefSize(14, 14);
				editToggleHelpButton.setStyle("-fx-background-color: transparent; -fx-border-color: transparent");
				editToggleHelpButton.setPadding(new Insets(-30));
				editToggleHelpButton.setFocusTraversable(false);
				final HBox editToggleHBox = new HBox(editToggle, editToggleHelpButton);

				editToggle.setPrefSize(14, 14);
				editToggle.setPadding(new Insets(-2));
				editToggle.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
				editToggle.setOnAction(_ -> {
					boolean editing = editToggle.isSelected();
					textArea.setEditable(editing);
					textArea.setFocusTraversable(editing);
					if (editing) return;
					try {
						ResponseEntity<String> response = httpClientUtil.patch(baseUrl + "/edit-solidity", null, DigitalizedContract.builder().id(contractId).soliditySource(textArea.getText()).build(), String.class);

						if (response.getStatusCode().is2xxSuccessful()) Platform.runLater(() -> {
							mainRefreshBtn.fire();
							String originalStyle = textArea.getStyle();

							Timeline blinkTimeline = new Timeline(
									new KeyFrame(Duration.ZERO, _ -> textArea.setStyle("-fx-control-inner-background: #b6fcb6;")),
									new KeyFrame(Duration.seconds(0.3), _ -> textArea.setStyle(originalStyle)),
									new KeyFrame(Duration.seconds(0.6), _ -> textArea.setStyle("-fx-control-inner-background: #b6fcb6;")),
									new KeyFrame(Duration.seconds(0.9), _ -> textArea.setStyle(originalStyle))
							);
							blinkTimeline.play();
						});
						else throw new HttpResponseException(response.getStatusCode().value(), response.getBody());
					} catch (Exception e) {
						log.error("Cannot edit prepared solidity contract", e);
						windowLauncher.launchErrorSpecialWindow("Error occurred while editing prepared solidity contract:\n" + e.getLocalizedMessage());
					}
				});


				windowController.getTitleBar().getChildren().add(3, editToggleHBox);
			}
		});
	}
}