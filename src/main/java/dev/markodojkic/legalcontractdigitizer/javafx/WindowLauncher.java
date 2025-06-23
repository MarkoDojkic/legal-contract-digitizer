package dev.markodojkic.legalcontractdigitizer.javafx;

import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.WebViewWindow;
import dev.markodojkic.legalcontractdigitizer.javafx.uiController.JavaFXWindowController;
import dev.markodojkic.legalcontractdigitizer.javafx.uiController.WindowAwareController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Component
public class WindowLauncher {

    @Autowired
    private ApplicationContext applicationContext;

    public void launchWindow(Stage stage, String windowTitle, double width, double height, String contentFxml, String contentCSS, Object ...controller) {
        try {
            // Load the main window frame (contains title bar, status bar, content area)
            FXMLLoader windowLoader = new FXMLLoader(getClass().getResource("/layout/window.fxml"));
            Parent windowRoot = windowLoader.load();

            JavaFXWindowController windowController = windowLoader.getController();
            windowController.setTitle(windowTitle);
            Scene scene = new Scene(windowRoot, width, height);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/static/style/window.css")).toExternalForm());

            // Load the specific content for the center area
            FXMLLoader contentLoader = new FXMLLoader(getClass().getResource(contentFxml));
            if(controller != null && controller.length > 0) {
                WindowAwareController contentController = (WindowAwareController) controller[0];
                contentController.setWindowController(windowController);
                contentLoader.setController(contentController); // Set custom controller if provided
            }

            Parent contentRoot = contentLoader.load(); // Loads corresponding controller too

            // Inject content into content area
            windowController.getContentArea().getChildren().setAll(contentRoot);

            // Set up scene and stage
            scene.getStylesheets().add(contentCSS);
            stage.setHeight(height);
            stage.setWidth(width);
            stage.setScene(scene);
            stage.setTitle(windowTitle);
            stage.setResizable(false);
            stage.initStyle(StageStyle.UNDECORATED);

            // Run animation AFTER scene is rendered
            Platform.runLater(() -> WindowAnimator.openWindow(
                    stage,
                    windowController.getTitleBar(),
                    windowController.getStatusBar(),
                    windowController.getContentArea()
            ));

        } catch (IOException e) {
            throw new RuntimeException("Failed to launch window with content: " + contentFxml, e);
        }
    }

    public WebViewWindow launchWebViewWindow(Stage stage, String title, double width, double height, String url) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/window.fxml"));
            Parent root = loader.load();
            JavaFXWindowController controller = loader.getController();
            controller.setTitle(title);

            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/static/style/window.css")).toExternalForm());

            WebView webView = new WebView();
            WebEngine engine = webView.getEngine();
            engine.load(url);

            controller.getContentArea().getChildren().setAll(webView);

            stage.setScene(scene);
            stage.setTitle(title);
            stage.setResizable(false);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setWidth(width);
            stage.setHeight(height);

            Platform.runLater(() -> WindowAnimator.openWindow(
                    stage,
                    controller.getTitleBar(),
                    controller.getStatusBar(),
                    controller.getContentArea()
            ));

            return new WebViewWindow(stage, controller, engine);
        } catch (IOException e) {
            throw new RuntimeException("Failed to launch WebView window for: " + url, e);
        }
    }

    public void launchFilePickerWindow(Stage stage, String title, double width, double height, Consumer<File> onFilePicked) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/window.fxml"));
            Parent root = loader.load();
            JavaFXWindowController controller = loader.getController();
            controller.setTitle(title);

            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/static/style/window.css")).toExternalForm());

            // Build file picker UI (button + label)
            Button pickFileBtn = new Button("Select Contract File");
            Label fileNameLabel = new Label("No file selected");
            VBox filePickerBox = new VBox(15, pickFileBtn, fileNameLabel);
            filePickerBox.setAlignment(Pos.CENTER);

            pickFileBtn.setOnAction(e -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Choose Contract File");
                File selectedFile = fileChooser.showOpenDialog(stage);
                if (selectedFile != null) {
                    fileNameLabel.setText(selectedFile.getName());
                    onFilePicked.accept(selectedFile);
                }
            });

            controller.getContentArea().getChildren().setAll(filePickerBox);

            stage.setScene(scene);
            stage.setTitle(title);
            stage.setResizable(false);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setWidth(width);
            stage.setHeight(height);

            Platform.runLater(() -> WindowAnimator.openWindow(
                    stage,
                    controller.getTitleBar(),
                    controller.getStatusBar(),
                    controller.getContentArea()
            ));
        } catch (IOException e) {
            throw new RuntimeException("Failed to launch File Picker window", e);
        }
    }

    public JavaFXWindowController launchSuccessAnimationWindow(Stage stage) {
        return launchAnimationWindow(stage, "Success", 250, 250, "/static/animations/green_check.gif");
    }

    public JavaFXWindowController launchErrorAnimationWindow(Stage stage) {
        return launchAnimationWindow(stage, "Error", 250, 250, "/static/animations/red_exclamation.gif");
    }

    private JavaFXWindowController launchAnimationWindow(Stage stage, String title, double width, double height, String gifResourcePath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/window.fxml"));
            Parent root = loader.load();
            JavaFXWindowController controller = loader.getController();
            controller.setTitle(title);

            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/static/style/window.css")).toExternalForm());

            ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream(gifResourcePath)));
            imageView.setFitWidth(width * 0.8);
            imageView.setPreserveRatio(true);

            VBox container = new VBox(imageView);
            container.setAlignment(Pos.CENTER);

            controller.getContentArea().getChildren().setAll(container);

            stage.setScene(scene);
            stage.setTitle(title);
            stage.setResizable(false);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setWidth(width);
            stage.setHeight(height);

            Platform.runLater(() -> WindowAnimator.openWindow(
                    stage,
                    controller.getTitleBar(),
                    controller.getStatusBar(),
                    controller.getContentArea()
            ));

            return controller;

        } catch (IOException e) {
            throw new RuntimeException("Failed to launch Animation window", e);
        }
    }
}
