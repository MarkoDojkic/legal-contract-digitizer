package dev.markodojkic.legalcontractdigitizer.javafx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import lombok.Getter;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
public class LoginController {

    private final Integer serverPort;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final WindowLauncher windowLauncher;

    @Getter
    private String authUrl;

    public LoginController(@Value("${server.port}") Integer serverPort,
                           @Value("${google.client.id}") String clientId,
                           @Value("${google.client.secret}") String clientSecret,
                           @Value("${google.redirect-uri}") String redirectUri,
                           @Autowired WindowLauncher windowLauncher) {
        this.serverPort = serverPort;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.authUrl = String.format("https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=openid%%20email%%20profile",
                clientId, redirectUri);
        this.windowLauncher = windowLauncher;
    }
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final OkHttpClient httpClient = new OkHttpClient();

    private Stage loginStage;

    @FXML private Button loginButton;
    @FXML private Label messageLabel;

    @FXML
    public void onLoginButtonClicked() {
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();

        // Open new stage to show Google login
        loginStage = new Stage();
        loginStage.setTitle("Google Sign-In");
        loginStage.setScene(new Scene(webView, 600, 600));
        loginStage.show();

        // Listen to URL changes to detect redirect with code
        webEngine.locationProperty().addListener((obs, oldLocation, newLocation) -> {
            if (newLocation.startsWith(redirectUri)) {
                // Extract the "code" query param
                String code = extractQueryParam(newLocation, "code");
                if (code != null) {
                    // Exchange code for ID token asynchronously
                    exchangeCodeForIdToken(code);
                } else {
                    String error = extractQueryParam(newLocation, "error");
                    messageLabel.setText("Login error: " + (error != null ? error : "Unknown"));
                    loginStage.close();
                }
            }
        });

        // Load Google OAuth URL
        webEngine.load(getAuthUrl());
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
                closeLoginStage();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    updateMessageLabel("Token request failed: " + response.message());
                    closeLoginStage();
                    return;
                }
                String responseBody = Objects.requireNonNull(response.body()).string();
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                String idToken = json.has("id_token") ? json.get("id_token").getAsString() : null;
                if (idToken == null || idToken.isEmpty()) {
                    updateMessageLabel("ID token missing in response");
                    closeLoginStage();
                    return;
                }

                // Send ID token to backend for verification
                sendIdTokenToBackend(idToken);

                closeLoginStage();
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
                        Stage welcomeStage = new Stage();
                        windowLauncher.launchWindow(
                                welcomeStage,
                                "Welcome",
                                600,
                                400,
                                "/layout/welcome.fxml",
                                "/static/style/welcome.css"
                        );

                        if (loginStage != null) {
                            loginStage.close();
                        }
                    });
                }
            }
        });
    }

    private void updateMessageLabel(String message) {
        javafx.application.Platform.runLater(() -> messageLabel.setText(message));
    }

    private void closeLoginStage() {
        if (loginStage != null) {
            javafx.application.Platform.runLater(() -> loginStage.close());
        }
    }

}