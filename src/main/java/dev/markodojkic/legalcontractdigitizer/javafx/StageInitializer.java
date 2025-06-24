package dev.markodojkic.legalcontractdigitizer.javafx;

import dev.markodojkic.legalcontractdigitizer.LCDJavaFxUIApplication.StageReadyEvent;
import dev.markodojkic.legalcontractdigitizer.javafx.controller.LoginController;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class StageInitializer implements ApplicationListener<StageReadyEvent> {

    private final ApplicationContext applicationContext;
    private final WindowLauncher windowLauncher;

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        windowLauncher.launchWindow(event.getStage(), "Legal contract digitizer - Login window", 500, 500, "/layout/login.fxml", Objects.requireNonNull(getClass().getResource("/static/style/login.css")).toExternalForm(), applicationContext.getBean(LoginController.class));
    }
}
