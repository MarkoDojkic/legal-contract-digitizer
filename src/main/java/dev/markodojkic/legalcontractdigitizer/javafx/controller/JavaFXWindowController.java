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
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class JavaFXWindowController {
    @FXML @Getter private BorderPane borderPane;
    @FXML @Getter private Button minimizeBtn, shrinkBtn, closeBtn;
    @FXML @Getter private HBox titleBar, statusBar;
    @FXML @Getter private StackPane contentArea;

    @FXML private Label titleLabel;

    @FXML @Getter @Setter private Pane windowRoot;
    @Setter private WindowLauncher windowLauncher;

    private boolean minimized = false;
    private boolean shrunk = false;

    @FXML
    private void initialize() {
        closeBtn.setOnAction(_ -> closeWindow());
        minimizeBtn.setOnAction(_ -> toggleMinimize());
        shrinkBtn.setOnAction(_ -> toggleShrink());
    }

    public void setTitle(String title) {
        if (titleLabel != null) titleLabel.setText(title);
    }

    private void closeWindow() {
        if (windowLauncher != null && windowRoot != null) {
            WindowAnimator.closeWindow(windowRoot, titleBar, statusBar, contentArea);

            PauseTransition delay = new PauseTransition(Duration.millis(800));
            delay.setOnFinished(_ -> {
                windowLauncher.getRootPane().getChildren().remove(windowRoot);
                if (windowLauncher.getRootPane().getChildren().isEmpty()) Platform.exit();
            });
            delay.play();
        }
    }

    private void toggleMinimize() {
        if (!minimized) {
            WindowAnimator.playAudio(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("static/audio/window_minimize.wav")));
            contentArea.setVisible(false);
            statusBar.setVisible(false);

            borderPane.getStyleClass().add("minimized");

            minimized = true;
        } else {
            WindowAnimator.playAudio(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("static/audio/window_maximize.wav")));
            contentArea.setVisible(true);
            statusBar.setVisible(true);

            borderPane.getStyleClass().remove("minimized");

            minimized = false;
        }
    }

    private void toggleShrink() {
        if (!shrunk) {
            WindowAnimator.playAudio(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("static/audio/window_scaleDown.wav")));
            // Scale down windowRoot to 45%
            windowRoot.setScaleX(0.45);
            windowRoot.setScaleY(0.45);
            shrunk = true;
        } else {
            WindowAnimator.playAudio(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("static/audio/window_scaleUp.wav")));
            // Restore scale
            windowRoot.setScaleX(1);
            windowRoot.setScaleY(1);
            shrunk = false;
        }
    }
}