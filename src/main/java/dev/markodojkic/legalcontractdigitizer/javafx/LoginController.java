package dev.markodojkic.legalcontractdigitizer.javafx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.markodojkic.legalcontractdigitizer.enumsAndRecords.WebViewWindow;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.NoArgsConstructor;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

@Component
public class LoginController implements WindowAwareController {
    private JavaFXWindowController windowController;

    private final Integer serverPort;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final WindowLauncher windowLauncher;
    private final ApplicationContext applicationContext;

    @Getter
    private String authUrl;

    public LoginController(@Value("${server.port}") Integer serverPort,
                           @Value("${google.client.id}") String clientId,
                           @Value("${google.client.secret}") String clientSecret,
                           @Value("${google.redirect-uri}") String redirectUri,
                           @Autowired WindowLauncher windowLauncher,
                           @Autowired ApplicationContext applicationContext) {
        this.serverPort = serverPort;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.authUrl = String.format("https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=openid%%20email%%20profile",
                clientId, redirectUri);
        this.windowLauncher = windowLauncher;
        this.applicationContext = applicationContext;
    }
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final OkHttpClient httpClient = new OkHttpClient();

    private JavaFXWindowController googleLoginController;

    @FXML private Button loginButton;
    @FXML private Label messageLabel;

    @FXML
    public void onLoginButtonClicked() {
        WebViewWindow googleLoginWindow = windowLauncher.launchWebViewWindow(
                new Stage(),
                "Google Sign-In",
                600,
                600,
                authUrl
        );

        googleLoginController = googleLoginWindow.controller(); // save this reference

        // Attach listener to *that* WebView
        googleLoginWindow.engine().locationProperty().addListener((obs, oldLoc, newLoc) -> {
            if (newLoc != null && newLoc.contains("code=")) {
                String code = extractQueryParam(newLoc, "code");
                if (code != null) {
                    exchangeCodeForIdToken(code);
                    closeGoogleLoginWindow();
                }
            } else if (newLoc.contains("error=")) {
                String error = extractQueryParam(newLoc, "error");
                updateMessageLabel("Login error: " + (error != null ? error : "Unknown"));
                closeGoogleLoginWindow();
            }
        });
    }


    private String extractQueryParam(String url, String param) {
        try {
            String[] parts = url.split("\\?");
            if (parts.length > 1) {
                String query = parts[1];
                for (String pair : query.split("&")) {
                    String[] keyVal = pair.split("=");
                    if (keyVal.length == 2 && keyVal[0].equals(param)) {
                        return URLDecoder.decode(keyVal[1], StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception _) {
            // ignore parsing errors
        }
        return null;
    }

    private void exchangeCodeForIdToken(String code) {
        // Build POST form parameters
        RequestBody formBody = new FormBody.Builder()
                .add("code", code)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("redirect_uri", redirectUri)
                .add("grant_type", "authorization_code")
                .build();

        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .post(formBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                updateMessageLabel("Failed to get token: " + e.getMessage());
                closeGoogleLoginWindow();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    updateMessageLabel("Token request failed: " + response.message());
                    closeGoogleLoginWindow();
                    return;
                }
                String responseBody = Objects.requireNonNull(response.body()).string();
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                String idToken = json.has("id_token") ? json.get("id_token").getAsString() : null;
                if (idToken == null || idToken.isEmpty()) {
                    updateMessageLabel("ID token missing in response");
                    closeGoogleLoginWindow();
                    return;
                }

                // Send ID token to backend for verification
                sendIdTokenToBackend(idToken);

                closeGoogleLoginWindow();
            }
        });
    }

    private void sendIdTokenToBackend(String idToken) {
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("idToken", idToken);

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(String.format("http://localhost:%s/api/v1/auth/google", serverPort))
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                updateMessageLabel("Backend auth failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    updateMessageLabel("Backend auth error: " + response.message());
                } else {
                    Platform.runLater(() -> {
                        WelcomeController welcomeController = applicationContext.getBean(WelcomeController.class);
                        JsonObject json;
                        try {
                            json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                        } catch (IOException e) {
                            updateMessageLabel("Backend auth error: " + e.getLocalizedMessage());
                            return;
                        }

                        welcomeController.setUserData(Map.of(
                                "name", json.get("name").getAsString(),
                                "email", json.get("email").getAsString(),
                                "userId", json.get("userId").getAsString()
                        ));


                        windowLauncher.launchWindow(
                                new Stage(),
                                "Welcome",
                                600,
                                400,
                                "/layout/welcome.fxml",
                                Objects.requireNonNull(getClass().getResource("/static/style/welcome.css")).toExternalForm(),
                                welcomeController
                        );
                        updateMessageLabel("Login successful");
                        closeGoogleLoginWindow();
                        windowController.getCloseBtn().fire();
                    });
                }
            }
        });
    }

    private void updateMessageLabel(String message) {
        javafx.application.Platform.runLater(() -> messageLabel.setText(message));
    }

    private void closeGoogleLoginWindow() {
        if (googleLoginController != null) {
            Platform.runLater(() -> googleLoginController.getCloseBtn().fire());
        }
    }

    @Override
    public void setWindowController(JavaFXWindowController controller) {
        this.windowController = controller;
    }
}