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
	public void initialize() {
		textArea.setText(text);
		textArea.setEditable(false);
		textArea.setFocusTraversable(false);
		if(windowController == null) return;

		if(contractId != null){
			ToggleButton editToggle = new ToggleButton("🧪️");
			editToggle.setMinSize(14, 14);
			editToggle.setMaxSize(14, 14);
			editToggle.setPadding(new Insets(-2));
			editToggle.getStyleClass().add("btn-action");
			editToggle.setOnAction(_ -> {
				boolean editing = editToggle.isSelected();
				textArea.setEditable(editing);
				textArea.setFocusTraversable(editing);
				if(editing) return;
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
			Platform.runLater(() -> windowController.getTitleBar().getChildren().add(3, editToggle));
		}
	}
}