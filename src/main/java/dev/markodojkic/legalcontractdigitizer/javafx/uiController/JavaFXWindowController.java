package dev.markodojkic.legalcontractdigitizer.javafx.uiController;

import dev.markodojkic.legalcontractdigitizer.javafx.WindowAnimator;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import lombok.Getter;
import org.springframework.stereotype.Component;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.layout.HBox;

@Component
@Getter
public class JavaFXWindowController {

    @FXML private StackPane contentArea;
    @FXML private Button minimizeBtn;
    @Getter
    @FXML private Button closeBtn;
    @FXML private Label windowTitle;
    @FXML private HBox titleBar;
    @FXML private HBox statusBar;
    @FXML private BorderPane borderPane;

    private double xOffset = 0;
    private double yOffset = 0;

    public void initialize() {
        closeBtn.setOnAction(e -> {
            Stage stage = (Stage) closeBtn.getScene().getWindow();
            WindowAnimator.closeWindow(stage, this.getTitleBar(), this.getStatusBar(), this.getContentArea());
        });
        minimizeBtn.setOnAction(e -> ((Stage) minimizeBtn.getScene().getWindow()).setIconified(true));

        // Make window draggable
        titleBar.setOnMousePressed(this::handleDragStart);
        titleBar.setOnMouseDragged(this::handleDragging);
    }

    private void handleDragStart(MouseEvent event) {
        Stage stage = (Stage) titleBar.getScene().getWindow();
        xOffset = stage.getX() - event.getScreenX();
        yOffset = stage.getY() - event.getScreenY();
    }

    private void handleDragging(MouseEvent event) {
        Stage stage = (Stage) titleBar.getScene().getWindow();
        stage.setX(event.getScreenX() + xOffset);
        stage.setY(event.getScreenY() + yOffset);
    }

    public void setTitle(String title) {
        windowTitle.setText(title);
    }
}
