package dev.markodojkic.legalcontractdigitizer.javafx;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.stereotype.Component;

@Component
public class WelcomeController {

    @FXML private Label messageLabel;

    public void setMessage(String message) {
        messageLabel.setText(message);
    }
}