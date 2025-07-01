package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.ApplicationContext;

@RequiredArgsConstructor
public abstract class WindowAwareController {
    @Setter
    @Getter
    protected JavaFXWindowController windowController;
    protected final WindowLauncher windowLauncher;
    protected final ApplicationContext applicationContext;
}