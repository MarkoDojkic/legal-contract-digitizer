package dev.markodojkic.legalcontractdigitizer.enumsAndRecords;

import dev.markodojkic.legalcontractdigitizer.javafx.uiController.JavaFXWindowController;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;

public record WebViewWindow(Stage stage, JavaFXWindowController controller, WebEngine engine) {}