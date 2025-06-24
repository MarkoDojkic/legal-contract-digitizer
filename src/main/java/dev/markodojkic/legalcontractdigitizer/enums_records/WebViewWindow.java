package dev.markodojkic.legalcontractdigitizer.enums_records;

import dev.markodojkic.legalcontractdigitizer.javafx.controller.JavaFXWindowController;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;

public record WebViewWindow(Stage stage, JavaFXWindowController controller, WebEngine engine) {}