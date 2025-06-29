package dev.markodojkic.legalcontractdigitizer.javafx;

import dev.markodojkic.legalcontractdigitizer.javafx.controller.JavaFXWindowController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import lombok.Getter;

import java.io.IOException;

public class WindowComponent extends Pane {

    @Getter
    private final JavaFXWindowController controller;

    public WindowComponent(String title, double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/window.fxml"));
            Parent root = loader.load();
            this.controller = loader.getController();
            controller.setTitle(title);

            ((Region) root).setPrefSize(width, height);
            getChildren().add(root);

            enableDragging(root);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load window.fxml", e);
        }
    }

    private double dragOffsetX;
    private double dragOffsetY;

    private void enableDragging(Parent root) {
        root.lookup("#titleBar").setOnMousePressed(event -> {
            dragOffsetX = event.getSceneX() - getLayoutX();
            dragOffsetY = event.getSceneY() - getLayoutY();
        });

        root.lookup("#titleBar").setOnMouseDragged(event -> {
            double newX = event.getSceneX() - dragOffsetX;
            double newY = event.getSceneY() - dragOffsetY;
            // Optionally: clamp to parent bounds here
            setLayoutX(newX);
            setLayoutY(newY);
        });
    }
}
