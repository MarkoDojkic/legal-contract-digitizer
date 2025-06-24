package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
public class WindowPreviewController implements WindowAwareController {
	@Setter
	@Getter
	private JavaFXWindowController windowController;

	@Setter
	private String text;

	@FXML private TextArea textArea;

	@FXML
	public void initialize() {
		textArea.setText(text);
		textArea.setEditable(false);
	}
}