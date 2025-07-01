package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class WindowPreviewController extends WindowAwareController {
	@Setter
	private String text;

	@FXML private TextArea textArea;

	@Autowired
	public WindowPreviewController(WindowLauncher windowLauncher, ApplicationContext applicationContext) {
		super(windowLauncher, applicationContext);
	}

	@FXML
	public void initialize() {
		textArea.setText(text);
		textArea.setEditable(false);
	}
}