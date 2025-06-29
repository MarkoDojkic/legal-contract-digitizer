package dev.markodojkic.legalcontractdigitizer.javafx;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.AudioClip;
import javafx.util.Duration;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Slf4j
@NoArgsConstructor
public class WindowAnimator {

    public static void openWindow(Pane windowRoot, HBox titleBar, HBox statusBar, StackPane contentArea) {
        windowRoot.setVisible(true); // needed if just added to scene
        Platform.runLater(() -> {
            new AudioClip(new File("src/main/resources/static/audio/window_opening_sc.wav").toURI().toString()).play();

            double targetHeight = titleBar.getHeight() + contentArea.getHeight() + statusBar.getHeight();
            double titleBarHeight = titleBar.getHeight() > 0 ? titleBar.getHeight() : 40;
            double statusBarHeight = statusBar.getHeight() > 0 ? statusBar.getHeight() : 30;
            double centerY = targetHeight / 2.0;

            titleBar.setTranslateY(centerY - titleBarHeight);
            statusBar.setTranslateY(-centerY + statusBarHeight);
            contentArea.setOpacity(0);
            titleBar.setOpacity(0);
            statusBar.setOpacity(0);

            Timeline blink = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(titleBar.opacityProperty(), 0),
                            new KeyValue(statusBar.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(50),
                            new KeyValue(titleBar.opacityProperty(), 1),
                            new KeyValue(statusBar.opacityProperty(), 1)),
                    new KeyFrame(Duration.millis(100),
                            new KeyValue(titleBar.opacityProperty(), 0),
                            new KeyValue(statusBar.opacityProperty(), 0)),
                    new KeyFrame(Duration.millis(200),
                            new KeyValue(titleBar.opacityProperty(), 1),
                            new KeyValue(statusBar.opacityProperty(), 1))
            );

            Timeline slideAndReveal = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(titleBar.translateYProperty(), centerY - titleBarHeight),
                            new KeyValue(statusBar.translateYProperty(), -centerY + statusBarHeight),
                            new KeyValue(contentArea.opacityProperty(), 0)
                    ),
                    new KeyFrame(Duration.millis(480),
                            new KeyValue(titleBar.translateYProperty(), 0),
                            new KeyValue(statusBar.translateYProperty(), 0),
                            new KeyValue(contentArea.opacityProperty(), 1)
                    )
            );

            SequentialTransition sequence = new SequentialTransition(blink, slideAndReveal);
            sequence.play();
        });
    }

    public static void closeWindow(Pane windowPane, HBox titleBar, HBox statusBar, StackPane contentArea) {
        new AudioClip(new File("src/main/resources/static/audio/window_closing_sc.wav").toURI().toString()).play();
        double currentHeight = windowPane.getHeight();
        double titleBarHeight = titleBar.getHeight() > 0 ? titleBar.getHeight() : 40;
        double statusBarHeight = statusBar.getHeight() > 0 ? statusBar.getHeight() : 30;
        double centerY = currentHeight / 2.0;

        Timeline slideAway = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(titleBar.translateYProperty(), 0),
                        new KeyValue(statusBar.translateYProperty(), 0),
                        new KeyValue(contentArea.opacityProperty(), 1)
                ),
                new KeyFrame(Duration.millis(600),
                        new KeyValue(titleBar.translateYProperty(), centerY - titleBarHeight),
                        new KeyValue(statusBar.translateYProperty(), -centerY + statusBarHeight),
                        new KeyValue(contentArea.opacityProperty(), 0)
                )
        );

        Timeline blink = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(titleBar.opacityProperty(), 1),
                        new KeyValue(statusBar.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(50),
                        new KeyValue(titleBar.opacityProperty(), 0),
                        new KeyValue(statusBar.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(100),
                        new KeyValue(titleBar.opacityProperty(), 1),
                        new KeyValue(statusBar.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(titleBar.opacityProperty(), 0),
                        new KeyValue(statusBar.opacityProperty(), 0))
        );

        SequentialTransition sequence = new SequentialTransition(slideAway, blink);
        sequence.play();
    }
}