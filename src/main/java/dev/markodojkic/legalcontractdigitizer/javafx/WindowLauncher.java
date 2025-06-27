package dev.markodojkic.legalcontractdigitizer.javafx;

import dev.markodojkic.legalcontractdigitizer.enums_records.WebViewWindow;
import dev.markodojkic.legalcontractdigitizer.javafx.controller.JavaFXWindowController;
import dev.markodojkic.legalcontractdigitizer.javafx.controller.WindowAwareController;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class WindowLauncher {

    private ApplicationContext applicationContext;

    private record StageWithController(Stage stage, JavaFXWindowController controller, Scene scene) {}

    private StageWithController setupStageWithWindow(Stage stage, String title, double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/window.fxml"));
            Parent root = loader.load();
            JavaFXWindowController controller = loader.getController();
            controller.setTitle(title);

            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/static/style/window.css")).toExternalForm());

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

            return new StageWithController(stage, controller, scene);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to setup window frame for: " + title, e);
        }
    }

    public <T extends WindowAwareController> T launchWindow(Stage stage, String windowTitle, double width, double height,
                                                            String contentFxml, String contentCSS, Object... controller) {
        StageWithController swc = setupStageWithWindow(stage, windowTitle, width, height);

        try {
            FXMLLoader contentLoader = new FXMLLoader(getClass().getResource(contentFxml));
            if (controller != null && controller.length > 0) {
                WindowAwareController contentController = (WindowAwareController) controller[0];
                contentController.setWindowController(swc.controller);
                contentLoader.setController(contentController);
            }
            Parent contentRoot = contentLoader.load();

            swc.controller.getContentArea().getChildren().setAll(contentRoot);
            if(contentCSS != null) swc.scene.getStylesheets().add(contentCSS);

            return contentLoader.getController();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to launch window content: " + contentFxml, e);
        }
    }

    public WebViewWindow launchWebViewWindow(Stage stage, String title, double width, double height, String url) {
        StageWithController swc = setupStageWithWindow(stage, title, width, height);

        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        engine.load(url);

        swc.controller.getContentArea().getChildren().setAll(webView);

        return new WebViewWindow(swc.stage, swc.controller, engine);
    }

    public void launchFilePickerWindow(Stage stage, String title, double width, double height, Consumer<File> onFilePicked) {
        StageWithController swc = setupStageWithWindow(stage, title, width, height);

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

        swc.controller.getContentArea().getChildren().setAll(filePickerBox);
    }

    public JavaFXWindowController launchSuccessAnimationWindow(Stage stage) {
        return launchAnimationWindow(stage, "Success", 250, 250, "/static/animations/green_check.gif");
    }

    public JavaFXWindowController launchErrorAnimationWindow(Stage stage) {
        return launchAnimationWindow(stage, "Error", 250, 250, "/static/animations/red_exclamation.gif");
    }

    private JavaFXWindowController launchAnimationWindow(Stage stage, String title, double width, double height, String gifResourcePath) {
        StageWithController swc = setupStageWithWindow(stage, title, width, height);

        ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream(gifResourcePath)));
        imageView.setFitWidth(width * 0.8);
        imageView.setPreserveRatio(true);

        VBox container = new VBox(imageView);
        container.setAlignment(Pos.CENTER);

        swc.controller.getContentArea().getChildren().setAll(container);

        return swc.controller;
    }
}