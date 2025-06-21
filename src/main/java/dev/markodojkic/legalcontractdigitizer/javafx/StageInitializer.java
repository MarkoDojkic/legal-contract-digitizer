package dev.markodojkic.legalcontractdigitizer.javafx;

import dev.markodojkic.legalcontractdigitizer.LCDJavaFxUIApplication.StageReadyEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StageInitializer implements ApplicationListener<StageReadyEvent> {

    private final WindowLauncher windowLauncher;

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        windowLauncher.launchWindow(event.getStage(), "Access Required", 350, 450, "/layout/login.fxml", "/static/style/login.css");
    }
}
