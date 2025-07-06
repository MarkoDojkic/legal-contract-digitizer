package dev.markodojkic.legalcontractdigitizer.javafx.controller;

import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.markodojkic.legalcontractdigitizer.LegalContractDigitizerApplication;
import dev.markodojkic.legalcontractdigitizer.exception.UnauthorizedAccessException;
import dev.markodojkic.legalcontractdigitizer.model.WebViewWindow;
import dev.markodojkic.legalcontractdigitizer.javafx.WindowLauncher;
import dev.markodojkic.legalcontractdigitizer.util.AuthSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import java.util.prefs.Preferences;

@Component
@Slf4j
public class LoginController extends WindowAwareController {

    private final String googleAuthUrl, googleRedirectUrl;
    private final GoogleAuthorizationCodeFlow authorizationCodeFlow;
    private final Preferences preferences = Preferences.userNodeForPackage(LegalContractDigitizerApplication.class);

    @SneakyThrows
    @Autowired
    public LoginController(@Value("${google.client.id}") String clientId,
                           @Value("${google.client.secret}") String clientSecret,
                           @Value("${google.authUrl}") String googleAuthUrl,
                           @Value("${google.redirectUrl}") String googleRedirectUrl,
                           WindowLauncher windowLauncher,
                           ApplicationContext applicationContext) {
        super(windowLauncher, applicationContext);
        this.googleAuthUrl = String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&scope=openid%%20email%%20profile&access_type=offline&prompt=consent", googleAuthUrl, clientId, googleRedirectUrl);
        this.googleRedirectUrl = googleRedirectUrl;

        // Initialize Google AuthorizationCodeFlow
        this.authorizationCodeFlow = new GoogleAuthorizationCodeFlow.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), clientId, clientSecret, Collections.singleton("openid profile email")).build();
    }

    @FXML
    public void onLoginBtnClicked() {
        AuthSession.setAccessToken(preferences.get("accessToken", null));
        AuthSession.setRefreshToken(preferences.get("refreshToken", null));
        if (AuthSession.hasAccessToken()) login("");
        else launchGoogleSignInFlow();
    }

    @FXML
    public void onLoginHelpBtnClicked() {
        windowLauncher.launchHelpSpecialWindow("You will be prompted to login with Google account. \nAccess will be granted directly if access token is stored (during previous successful login).\nIn case of access token expiration, refresh token will be used to renew access token.");
    }

    private String extractQueryParam(String url, String param) {
        try {
            String[] parts = url.split("\\?");
            if (parts.length > 1) {
                String query = parts[1];
                for (String pair : query.split("&")) {
                    String[] keyVal = pair.split("=");
                    if (keyVal.length == 2 && keyVal[0].equals(param)) return URLDecoder.decode(keyVal[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception _) { /* Ignore parsing errors */}
        return null;
    }

    private void exchangeCodeForTokens(String code) {
        try {
            // Use GoogleAuthorizationCodeFlow to exchange the code for tokens
            GoogleTokenResponse tokenResponse = authorizationCodeFlow.newTokenRequest(code)
                    .setRedirectUri(googleRedirectUrl)
                    .execute();

            // Save tokens and login
            preferences.put("accessToken", tokenResponse.getAccessToken());
            preferences.put("refreshToken", tokenResponse.getRefreshToken());

            AuthSession.setAccessToken(tokenResponse.getAccessToken());
            AuthSession.setRefreshToken(tokenResponse.getRefreshToken());
            login(tokenResponse.getIdToken());
        } catch (IOException e) {
            log.error("Error occurred during token exchange: {}", e.getLocalizedMessage());
            windowLauncher.launchErrorSpecialWindow("Error occurred during token exchange: " + e.getLocalizedMessage());
        }
    }

    private void login(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length == 3) {
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                // Parse the payload to extract user data
                JsonObject jsonObject = JsonParser.parseString(payload).getAsJsonObject();

                // Store this user data for use in the app
                preferences.put("name", jsonObject.get("name").getAsString());
                preferences.put("email", jsonObject.get("email").getAsString());
            } else if(!AuthSession.hasAccessToken()) throw new UnauthorizedAccessException("Invalid ID token structure.");

            // Launch main window after successful login
            windowLauncher.launchWindow("Main window", 1280, 1024, "/layout/main.fxml", Objects.requireNonNull(getClass().getResource("/static/style/main.css")).toExternalForm(), applicationContext.getBean(MainController.class));
            windowLauncher.launchSuccessSpecialWindow("Login successful!");
            windowController.getCloseBtn().fire();
        } catch (Exception e) {
            log.error("Error occurred during login: {}", e.getLocalizedMessage());
            windowLauncher.launchErrorSpecialWindow("Error occurred during login: " + e.getLocalizedMessage());
        }
    }

    private void launchGoogleSignInFlow() {
        WebViewWindow googleLoginWindow = windowLauncher.launchWebViewWindow("Google Sign-In", 1024, 768, googleAuthUrl);

        googleLoginWindow.engine().locationProperty().addListener((_, _, newLoc) -> {
            if (newLoc != null && newLoc.contains("code=")) {
                Platform.runLater(() -> {
                    String code = extractQueryParam(newLoc, "code");
                    if (code != null) exchangeCodeForTokens(code);
                    googleLoginWindow.controller().getCloseBtn().fire();
                });
            } else if (newLoc != null && newLoc.contains("error=")) {
                String error = extractQueryParam(newLoc, "error");
                windowLauncher.launchErrorSpecialWindow("Google login window was redirected to: " + (error != null ? error : "Unknown"));

                if (googleLoginWindow.controller() != null) Platform.runLater(() -> googleLoginWindow.controller().getCloseBtn().fire());
            }
        });
    }
}