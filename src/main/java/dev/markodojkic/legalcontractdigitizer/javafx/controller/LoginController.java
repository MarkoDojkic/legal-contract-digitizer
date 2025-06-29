package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.markodojkic.legalcontractdigitizer.LegalContractDigitizerApplication;
import dev.markodojkic.legalcontractdigitizer.enums_records.WebViewWindow;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.AuthSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import lombok.Getter;
import lombok.Setter;
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
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

@Component
public class LoginController implements WindowAwareController {
    public static final String EMAIL = "email";
    public static final String USER_ID = "userId";
    public static final String ID_TOKEN = "idToken";
    @Setter
    @Getter
    private JavaFXWindowController windowController;

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final WindowLauncher windowLauncher;
    private final ApplicationContext applicationContext;

    private final String backendUrl;
    private final String authUrl;

    public LoginController(@Value("${server.port}") Integer serverPort,
                           @Value("${google.client.id}") String clientId,
                           @Value("${google.client.secret}") String clientSecret,
                           @Value("${google.redirect-uri}") String redirectUri,
                           @Autowired WindowLauncher windowLauncher,
                           @Autowired ApplicationContext applicationContext) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.authUrl = String.format("https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=openid%%20email%%20profile",
                clientId, redirectUri);
        this.backendUrl = String.format("http://localhost:%s/api/v1/auth/google", serverPort);
        this.windowLauncher = windowLauncher;
        this.applicationContext = applicationContext;
    }
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    Preferences prefs = Preferences.userNodeForPackage(LegalContractDigitizerApplication.class);

    @FXML private Button loginButton;
    @FXML private Label messageLabel;

    @FXML
    public void onLoginButtonClicked() {
        String cachedToken = prefs.get(ID_TOKEN, null);
        if (cachedToken != null) {
            sendIdTokenToBackend(cachedToken, true);
        } else launchGoogleSignInFlow();
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

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                updateMessageLabel("Failed to get token: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    updateMessageLabel("Token request failed: " + response.message());
                    return;
                }
                String responseBody = Objects.requireNonNull(response.body()).string();
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                String idToken = json.has("id_token") ? json.get("id_token").getAsString() : null;
                if (idToken == null || idToken.isEmpty()) {
                    updateMessageLabel("ID token missing in response");
                    return;
                }

                // Send ID token to backend for verification
                sendIdTokenToBackend(idToken, false);
            }
        });
    }

    private void sendIdTokenToBackend(String idToken, boolean fromCache) {
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty(ID_TOKEN, idToken);

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(backendUrl)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (fromCache) {
                    // Clear corrupted or expired cached token
                    prefs.remove(ID_TOKEN);
                    AuthSession.setIdToken(null);
                    Platform.runLater(() -> launchGoogleSignInFlow());
                } else {
                    updateMessageLabel("Backend auth failed: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (fromCache) {
                        prefs.remove(ID_TOKEN);
                        AuthSession.setIdToken(null);
                        Platform.runLater(() -> launchGoogleSignInFlow());
                    } else {
                        updateMessageLabel("Backend auth error: " + response.message());
                    }
                } else {
                    Platform.runLater(() -> {
                        MainController mainController = applicationContext.getBean(MainController.class);
                        JsonObject json;
                        try {
                            json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                        } catch (IOException e) {
                            updateMessageLabel("Backend auth error: " + e.getLocalizedMessage());
                            return;
                        }

                        mainController.setUserData(Map.of(
                                "name", json.get("name").getAsString(),
                                EMAIL, json.get(EMAIL).getAsString(),
                                USER_ID, json.get(USER_ID).getAsString()
                        ));

                        prefs.put("name", json.get("name").getAsString());
                        prefs.put(EMAIL, json.get(EMAIL).getAsString());
                        prefs.put(USER_ID, json.get(USER_ID).getAsString());
                        prefs.put(ID_TOKEN, idToken);
                        AuthSession.setIdToken(idToken);

                        windowLauncher.launchWindow(
                                "Main window",
                                1280,
                                1024,
                                "/layout/main.fxml",
                                Objects.requireNonNull(getClass().getResource("/static/style/main.css")).toExternalForm(),
                                mainController
                        );
                        updateMessageLabel("Login successful");
                        windowController.getCloseButton().fire();
                    });
                }
            }
        });
    }

    private void launchGoogleSignInFlow() {
        WebViewWindow googleLoginWindow = windowLauncher.launchWebViewWindow(
                "Google Sign-In",
                1024,
                768,
                authUrl
        );


        // Attach listener to *that* WebView
        googleLoginWindow.engine().locationProperty().addListener((obs, oldLoc, newLoc) -> {
            if (newLoc != null && newLoc.contains("code=")) {
                String code = extractQueryParam(newLoc, "code");
                if (code != null) exchangeCodeForIdToken(code);

                Platform.runLater(() -> googleLoginWindow.controller().getCloseButton().fire());
            } else if (newLoc.contains("error=")) {
                String error = extractQueryParam(newLoc, "error");
                updateMessageLabel("Login error: " + (error != null ? error : "Unknown"));

                if (googleLoginWindow.controller() != null) {
                    Platform.runLater(() -> googleLoginWindow.controller().getCloseButton().fire());
                }
            }
        });
    }

    private void updateMessageLabel(String message) {
        javafx.application.Platform.runLater(() -> messageLabel.setText(message));
    }
}