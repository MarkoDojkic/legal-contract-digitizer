package dev.markodojkic.legalcontractdigitizer.javafx;

import dev.markodojkic.legalcontractdigitizer.enums_records.WebViewWindow;
import dev.markodojkic.legalcontractdigitizer.javafx.controller.JavaFXWindowController;
import dev.markodojkic.legalcontractdigitizer.javafx.controller.WindowAwareController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Component
public class WindowLauncher {

    @Setter
    @Getter
    private Pane rootPane; // main container for all window panes

    private final ApplicationContext applicationContext;

    public WindowLauncher(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Launch window with given title, size, content FXML and CSS, and controller instance.
     */
    public <T extends WindowAwareController> T launchWindow(
            String windowTitle,
            double width,
            double height,
            String contentFxml,
            String contentCSS,
            T controllerInstance) {

        try {
            FXMLLoader windowLoader = new FXMLLoader(getClass().getResource("/layout/window.fxml"));
            Pane windowRoot = windowLoader.load();
            JavaFXWindowController windowController = windowLoader.getController();

            windowController.setTitle(windowTitle);
            windowController.setWindowRoot(windowRoot);
            windowController.setWindowLauncher(this);
            windowRoot.setPrefWidth(width);
            windowRoot.setPrefHeight(height);

            FXMLLoader contentLoader = new FXMLLoader(getClass().getResource(contentFxml));
            contentLoader.setController(controllerInstance);
            Parent contentRoot = contentLoader.load();

            // Setup window controller relationship
            controllerInstance.setWindowController(windowController);

            windowController.getContentArea().getChildren().setAll(contentRoot);

            windowRoot.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/static/style/window.css")).toExternalForm());
            if (contentCSS != null) {
                windowRoot.getStylesheets().add(contentCSS);
            }

            rootPane.getChildren().add(windowRoot);

            centerWindow(windowRoot, width, height);

            makeDraggable(windowRoot, windowController.getTitleBar());

            WindowAnimator.openWindow(rootPane, windowController.getTitleBar(), windowController.getStatusBar(), windowController.getContentArea());

            return controllerInstance;

        } catch (IOException e) {
            throw new IllegalStateException("Failed to launch window content: " + contentFxml, e);
        }
    }

    public WebViewWindow launchWebViewWindow(String title, double width, double height, String url) {
        JavaFXWindowController controller = loadWindowFrame(title, width, height);

        WebView webView = new WebView();
        webView.getEngine().load(url);
        controller.getContentArea().getChildren().setAll(webView);

        return new WebViewWindow(rootPane, controller, webView.getEngine());
    }

    public void launchFilePickerWindow(String title, double width, double height, Consumer<File> onFilePicked) {
        JavaFXWindowController controller = loadWindowFrame(title, width, height);

        Label fileLabel = new Label("No file selected");
        Button selectButton = new Button("Choose File");
        VBox box = new VBox(10, selectButton, fileLabel);
        box.setAlignment(Pos.CENTER);

        selectButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select a file");
            File file = fileChooser.showOpenDialog(null); // still uses system dialog
            if (file != null) {
                fileLabel.setText(file.getName());
                onFilePicked.accept(file);
            }
        });

        controller.getContentArea().getChildren().setAll(box);
    }

    public void launchSuccessAnimationWindow() {
        launchAnimationWindow("Success", 250, 250, "/static/animations/green_check.gif");
    }

    public void launchErrorAnimationWindow() {
        launchAnimationWindow("Error", 250, 250, "/static/animations/red_exclamation.gif");
    }

    private void launchAnimationWindow(String title, double width, double height, String gifPath) {
        JavaFXWindowController controller = loadWindowFrame(title, width, height);

        ImageView image = new ImageView(new Image(Objects.requireNonNull(getClass().getResourceAsStream(gifPath))));
        image.setFitWidth(width * 0.8);
        image.setPreserveRatio(true);

        VBox box = new VBox(image);
        box.setAlignment(Pos.CENTER);

        controller.getContentArea().getChildren().setAll(box);
    }

    private JavaFXWindowController loadWindowFrame(String title, double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/window.fxml"));
            Pane windowRoot = loader.load();
            JavaFXWindowController controller = loader.getController();

            controller.setTitle(title);
            controller.setWindowRoot(windowRoot);     // so it can remove itself
            controller.setWindowLauncher(this);     // so it knows where to remove from
            windowRoot.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/static/style/window.css")).toExternalForm());

            windowRoot.setPrefSize(width, height);
            windowRoot.getStyleClass().add("window-wrapper");
            makeDraggable(windowRoot, controller.getTitleBar());

            Platform.runLater(() -> WindowAnimator.openWindow(rootPane, controller.getTitleBar(), controller.getStatusBar(), controller.getContentArea()));

            rootPane.getChildren().add(windowRoot); // this is your fullscreen root Pane

            return controller;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load window.fxml", e);
        }
    }

    /**
     * Make a window pane draggable by dragging on title bar.
     */
    private void makeDraggable(Parent window, Pane dragHandle) {
        final Delta dragDelta = new Delta();

        dragHandle.setOnMousePressed(mouseEvent -> {
            dragDelta.x = window.getLayoutX() - mouseEvent.getSceneX();
            dragDelta.y = window.getLayoutY() - mouseEvent.getSceneY();
        });

        dragHandle.setOnMouseDragged(mouseEvent -> {
            double newX = mouseEvent.getSceneX() + dragDelta.x;
            double newY = mouseEvent.getSceneY() + dragDelta.y;

            // Optional: clamp positions within rootPane bounds
            if (rootPane != null) {
                newX = clamp(newX, 0, rootPane.getWidth() - window.prefWidth(-1));
                newY = clamp(newY, 0, rootPane.getHeight() - window.prefHeight(-1));
            }

            window.setLayoutX(newX);
            window.setLayoutY(newY);
        });
    }

    private double clamp(double val, double min, double max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }

    private void centerWindow(Parent windowRoot, double width, double height) {
        if (rootPane != null) {
            windowRoot.setLayoutX((rootPane.getWidth() - width) / 2);
            windowRoot.setLayoutY((rootPane.getHeight() - height) / 2);
        }
    }

    private static class Delta { double x, y; }
}