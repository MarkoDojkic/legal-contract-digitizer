package dev.markodojkic.legalcontractdigitizer.javafx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class WindowLauncher {

    @Autowired
    private ApplicationContext applicationContext;

    public void launchWindow(Stage stage, String windowTitle, double width, double height, String contentFxml, String contentCSS) {
        try {
            // Load the main window frame (contains title bar, status bar, content area)
            FXMLLoader windowLoader = new FXMLLoader(getClass().getResource("/layout/window.fxml"));
            Parent windowRoot = windowLoader.load();

            JavaFXWindowController windowController = windowLoader.getController();
            windowController.setTitle(windowTitle);

            // Load the specific content for the center area
            FXMLLoader contentLoader = new FXMLLoader(getClass().getResource(contentFxml));
            contentLoader.setController(applicationContext.getBean(contentFxml.replaceAll(".*/|\\.fxml", "").concat("Controller"))); // Use bean name as controller
            Parent contentRoot = contentLoader.load(); // Loads corresponding controller too

            // Inject content into content area
            windowController.getContentArea().getChildren().setAll(contentRoot);

            // Set up scene and stage
            Scene scene = new Scene(windowRoot, width, height);
            scene.getStylesheets().add(getClass().getResource("/static/style/window.css").toExternalForm());
            scene.getStylesheets().add(getClass().getResource(contentCSS).toExternalForm());
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
}
