package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import dev.markodojkic.legalcontractdigitizer.javafx.WindowAnimator;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.AudioClip;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class JavaFXWindowController {
    @FXML public BorderPane borderPane;
    @FXML public Button minimizeBtn;
    @FXML public Button shrinkBtn;
    @FXML @Getter private HBox titleBar;
    @FXML @Getter private HBox statusBar;
    @FXML @Getter private StackPane contentArea;

    @FXML private Label titleLabel;
    @Getter
    @FXML private Button closeButton;

    @Getter @Setter private Pane windowRoot;
    @Setter private WindowLauncher windowLauncher;

    // Track states for toggling
    private boolean minimized = false;
    private boolean shrunk = false;

    @FXML
    private void initialize() {
        closeButton.setOnAction(event -> closeWindow());

        minimizeBtn.setOnAction(event -> toggleMinimize());
        shrinkBtn.setOnAction(event -> toggleShrink());
    }

    public void setTitle(String title) {
        if (titleLabel != null) {
            titleLabel.setText(title);
        }
    }

    private void closeWindow() {
        if (windowLauncher != null && windowRoot != null) {
            WindowAnimator.closeWindow(windowRoot, titleBar, statusBar, contentArea);

            PauseTransition delay = new PauseTransition(Duration.millis(800));
            delay.setOnFinished(e -> {
                windowLauncher.getRootPane().getChildren().remove(windowRoot);
                if (windowLauncher.getRootPane().getChildren().isEmpty()) {
                    Platform.exit();
                }
            });
            delay.play();
        }
    }

    private void toggleMinimize() {
        if (!minimized) {
            new AudioClip(new File("src/main/resources/static/audio/window_minimize.wav").toURI().toString()).play();
            contentArea.setVisible(false);
            statusBar.setVisible(false);

            borderPane.getStyleClass().add("minimized");

            minimized = true;
        } else {
            new AudioClip(new File("src/main/resources/static/audio/window_maximize.wav").toURI().toString()).play();
            contentArea.setVisible(true);
            statusBar.setVisible(true);

            borderPane.getStyleClass().remove("minimized");

            minimized = false;
        }
    }

    private void toggleShrink() {
        if (!shrunk) {
            new AudioClip(new File("src/main/resources/static/audio/window_scaleDown.wav").toURI().toString()).play();
            // Scale down windowRoot to 45%
            windowRoot.setScaleX(0.45);
            windowRoot.setScaleY(0.45);
            shrunk = true;
        } else {
            new AudioClip(new File("src/main/resources/static/audio/window_scaleUp.wav").toURI().toString()).play();
            // Restore scale
            windowRoot.setScaleX(1);
            windowRoot.setScaleY(1);
            shrunk = false;
        }
    }
}