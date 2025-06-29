package dev.markodojkic.legalcontractdigitizer.enums_records;

import dev.markodojkic.legalcontractdigitizer.javafx.controller.JavaFXWindowController;
import javafx.scene.Parent;
import javafx.scene.web.WebEngine;

public record WebViewWindow(Parent parent, JavaFXWindowController controller, WebEngine engine) {}