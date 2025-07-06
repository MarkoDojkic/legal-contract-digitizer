package dev.markodojkic.legalcontractdigitizer.javafx;

import dev.markodojkic.legalcontractdigitizer.model.SpecialBackgroundType;
import dev.markodojkic.legalcontractdigitizer.model.WebViewWindow;
import dev.markodojkic.legalcontractdigitizer.javafx.controller.JavaFXWindowController;
import dev.markodojkic.legalcontractdigitizer.javafx.controller.WindowAwareController;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Component
@Setter
@Getter
public class WindowLauncher {

    private Pane rootPane;

    public <T extends WindowAwareController> T launchWindow(String windowTitle, double width, double height, String contentFxml, String contentCSS, T controllerInstance) {

        JavaFXWindowController windowController = loadWindowFrame(windowTitle, width, height, false);
        Pane windowRoot = windowController.getWindowRoot();

        try {
            FXMLLoader contentLoader = new FXMLLoader(getClass().getResource(contentFxml));
            contentLoader.setController(controllerInstance);
            Parent contentRoot = contentLoader.load();

            controllerInstance.setWindowController(windowController);
            windowController.getContentArea().getChildren().setAll(contentRoot);

            if (contentCSS != null) windowRoot.getStylesheets().add(contentCSS);

            return controllerInstance;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load content: " + contentFxml, e);
        }
    }

    public WebViewWindow launchWebViewWindow(String title, double width, double height, String url) {
        JavaFXWindowController controller = loadWindowFrame(title, width, height, false);

        WebView webView = new WebView();
        webView.getEngine().load(url);
        controller.getContentArea().getChildren().setAll(webView);

        return new WebViewWindow(rootPane, controller, webView.getEngine());
    }

    public void launchFilePickerWindow(String title, double width, double height, Consumer<File> onFilePicked) {
        JavaFXWindowController controller = loadWindowFrame(title, width, height, false);

        Label fileLabel = new Label("No file selected");
        Button selectButton = new Button("Choose File");
        VBox box = new VBox(10, selectButton, fileLabel);
        box.setAlignment(Pos.CENTER);

        selectButton.setOnAction(_ -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select a file");
            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                fileLabel.setText("Uploading: " + file.getName());
                onFilePicked.accept(file);
            }
        });

        controller.getContentArea().getChildren().setAll(box);
    }

    public void launchErrorSpecialWindow(String text) {
        launchSpecialWindow("Error occurred", text, SpecialBackgroundType.ERROR, "/static/images/bigIcon_error.png");
    }

    public void launchSuccessSpecialWindow(String text) {
        launchSpecialWindow("Action completed successfully", text, SpecialBackgroundType.SUCCESS, "/static/images/bigIcon_plus.png");
    }

    public void launchWarnSpecialWindow(String text) {
        launchSpecialWindow("Warning", text, SpecialBackgroundType.WARN, "/static/images/bigIcon_yellowWarning.png");
    }

    public void launchInfoSpecialWindow(String text) {
        launchSpecialWindow("Information", text, SpecialBackgroundType.INFO, "/static/images/bigIcon_exclamation_info.png");
    }

    public void launchHelpSpecialWindow(String text) {
        launchSpecialWindow("Help", text, SpecialBackgroundType.HELP, "/static/images/bigIcon_exclamation_help.png");
    }

    private void launchSpecialWindow(String title, String text, SpecialBackgroundType specialBackgroundType, String iconPath) {
        double width = 512;
        double height = 512;

        JavaFXWindowController controller = loadWindowFrame(title, width, height, true);
        Pane contentArea = controller.getContentArea();

        Image backgroundImage = new Image("/static/images/special_backgrounds.png");
        int sliceWidth = 51;
        int sliceHeight = 1024;
        int offsetX = specialBackgroundType.getOffsetX();

        ImageView backgroundView = new ImageView(backgroundImage);
        backgroundView.setViewport(new Rectangle2D(offsetX, 0, sliceWidth, sliceHeight));
        backgroundView.setFitWidth(width);
        backgroundView.setFitHeight(height);
        backgroundView.setPreserveRatio(false);

        ImageView iconView = new ImageView(new Image(iconPath));
        iconView.setFitWidth(256);
        iconView.setFitHeight(256);
        iconView.setPreserveRatio(true);

        StackPane centerPane = new StackPane();
        centerPane.setPrefSize(width, height);
        centerPane.setAlignment(Pos.CENTER);
        centerPane.getChildren().add(iconView);

        ScaleTransition pulse = new ScaleTransition(Duration.millis(250), iconView);
        pulse.setFromX(0.8);
        pulse.setFromY(0.8);
        pulse.setToX(1.3);
        pulse.setToY(1.3);
        pulse.setCycleCount(10);
        pulse.setAutoReverse(true);

        Label animatedText = new Label(text);
        animatedText.setFont(Font.font("Gunship Condensed IFSCL", FontWeight.BOLD, 16.0));
        animatedText.setTextFill(specialBackgroundType.getTextColor());
        animatedText.setWrapText(true);
        animatedText.setTextAlignment(TextAlignment.CENTER);
        animatedText.setVisible(false);
        animatedText.setStyle("-fx-stroke: white; -fx-stroke-width: 1; -fx-effect: dropshadow( gaussian , black , 2 , 0.5 , 0 , 0 );");

        pulse.setOnFinished(_ -> {
            centerPane.getChildren().clear();
            centerPane.getChildren().add(animatedText);
            animatedText.setVisible(true);
        });

        pulse.play();

        StackPane finalLayout = new StackPane(backgroundView, centerPane);
        contentArea.getChildren().setAll(finalLayout);

        // Shift to right quarter after centering
        shiftToRightQuarter(controller.getWindowRoot(), width);
    }

    private JavaFXWindowController loadWindowFrame(String title, double width, double height, boolean isSpecialWindow) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/window.fxml"));
            Pane windowRoot = loader.load();
            JavaFXWindowController controller = loader.getController();

            controller.setTitle(title);
            controller.setWindowRoot(windowRoot);
            controller.setWindowLauncher(this);
            windowRoot.setPrefSize(width, height);
            windowRoot.getStyleClass().add("window-wrapper");
            windowRoot.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/static/style/window.css")).toExternalForm());

            makeDraggable(windowRoot, controller.getTitleBar());

            rootPane.getChildren().add(windowRoot);
            centerWindow(windowRoot, width, height);

            Platform.runLater(() -> WindowAnimator.openWindow(rootPane, controller.getTitleBar(), controller.getStatusBar(), controller.getContentArea(), isSpecialWindow));

            return controller;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load window.fxml", e);
        }
    }

    private void centerWindow(Parent windowRoot, double width, double height) {
        if (rootPane != null) {
            windowRoot.setLayoutX((rootPane.getWidth() - width) / 2);
            windowRoot.setLayoutY((rootPane.getHeight() - height) / 2);
        }
    }

    private void shiftToRightQuarter(Parent windowRoot, double width) {
        if (rootPane != null) {
            double centerX = (rootPane.getWidth() - width) / 2;
            double shiftX = rootPane.getWidth() * 0.25;
            windowRoot.setLayoutX(centerX + shiftX);
        }
    }

    private void makeDraggable(Parent window, Pane dragHandle) {
        final Delta dragDelta = new Delta();

        dragHandle.setOnMousePressed(e -> {
            dragDelta.x = window.getLayoutX() - e.getSceneX();
            dragDelta.y = window.getLayoutY() - e.getSceneY();
        });

        dragHandle.setOnMouseDragged(e -> {
            double newX = e.getSceneX() + dragDelta.x;
            double newY = e.getSceneY() + dragDelta.y;

            if (rootPane != null) {
                newX = Math.clamp(newX, 0, rootPane.getWidth() - window.prefWidth(-1));
                newY = Math.clamp(newY, 0, rootPane.getHeight() - window.prefHeight(-1));
            }

            window.setLayoutX(newX);
            window.setLayoutY(newY);
        });
    }

    private static class Delta {
        double x, y;
    }
}