package dev.markodojkic.legalcontractdigitizer.javafx;

import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.WebViewWindow;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

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
                contentLoader.setController(controller[0]); // Set custom controller if provided
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
}
