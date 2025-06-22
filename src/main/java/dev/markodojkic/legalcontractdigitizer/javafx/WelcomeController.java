package dev.markodojkic.legalcontractdigitizer.javafx;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WelcomeController {

    @FXML private Label nameLabel;
    @FXML private Label emailLabel;
    @FXML private Label userIdLabel;

    public void setUserData(Map<String, String> userData) {
        nameLabel.setText(userData.getOrDefault("name", "N/A"));
        emailLabel.setText(userData.getOrDefault("email", "N/A"));
        userIdLabel.setText(userData.getOrDefault("userId", "N/A"));
    } //TODO: This cannot be done here, as the controller is not initialized yet. Use a method in WindowAwareController to set this data after the window is ready.
}