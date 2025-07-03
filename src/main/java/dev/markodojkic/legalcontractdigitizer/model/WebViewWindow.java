package dev.markodojkic.legalcontractdigitizer.model;

import dev.markodojkic.legalcontractdigitizer.javafx.controller.JavaFXWindowController;
import javafx.scene.Parent;
import javafx.scene.web.WebEngine;

/**
 * Holds components related to a JavaFX WebView window.
 *
 * @param parent     The parent UI node.
 * @param controller The JavaFX window controller.
 * @param engine     The WebEngine instance.
 */
public record WebViewWindow(
        Parent parent,
        JavaFXWindowController controller,
        WebEngine engine
) {}