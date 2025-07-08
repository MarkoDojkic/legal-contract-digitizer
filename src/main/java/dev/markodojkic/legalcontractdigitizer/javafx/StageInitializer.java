package dev.markodojkic.legalcontractdigitizer.javafx;

import dev.markodojkic.legalcontractdigitizer.LCDJavaFxUIApplication.StageReadyEvent;
import dev.markodojkic.legalcontractdigitizer.javafx.controller.LoginController;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class StageInitializer implements ApplicationListener<StageReadyEvent> {

    private final WindowLauncher windowLauncher;
    private final LoginController loginController;

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        Stage primaryStage = event.getStage();

        // Create root Pane that will hold all windows (panes)
        Pane root = new Pane();
        Scene scene = new Scene(root); // full screen size

        root.setStyle("-fx-background-image: url('/static/images/background.png'); -fx-background-repeat: no-repeat; -fx-background-size: 100% auto;");
        primaryStage.getIcons().addAll(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/static/images/icon.png"))));
        primaryStage.setScene(scene);
        primaryStage.setTitle("Legal Contract Digitizer");
        primaryStage.setFullScreen(true);
        primaryStage.setFullScreenExitHint("Exit using ESC or automatically by closing all windows inside application");
        primaryStage.show();

        // Initialize launcher with root container Pane
        windowLauncher.setRootPane(root);

        // Launch login window as a pane inside root
        windowLauncher.launchWindow("Login window", 500, 500, "/layout/login.fxml", Objects.requireNonNull(getClass().getResource("/static/style/login.css")).toExternalForm(), loginController);
    }
}