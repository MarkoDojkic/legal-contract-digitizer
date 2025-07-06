package dev.markodojkic.legalcontractdigitizer.util;

import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Tokeninfo;
import dev.markodojkic.legalcontractdigitizer.LegalContractDigitizerApplication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * A filter that validates Google OAuth2 access tokens and sets up Spring Security authentication context.
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${spring.application.name}")
    private String appName;

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    private final HttpTransport httpTransport = new NetHttpTransport();
    private final JsonFactory jsonFactory = new GsonFactory();

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain) throws IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            unauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String accessToken = authHeader.substring(7);

        try {
            Tokeninfo tokenInfo = extractTokenInfo(accessToken);

            if (tokenInfo == null && !AuthSession.hasRefreshToken()) {
                unauthorized(response, "Invalid access token");
                return;
            }

            // Verify the token audience (client ID)
            if (tokenInfo != null && !clientId.equals(tokenInfo.getAudience())) {
                unauthorized(response, "Access token audience mismatch");
                return;
            }

            // Check if token expires within next 5 minutes (300 seconds)
            if (tokenInfo == null || tokenInfo.getExpiresIn() <= 300) {
                String refreshToken = AuthSession.getRefreshToken();

                if (refreshToken == null || refreshToken.isEmpty()) {
                    unauthorized(response, "No refresh token available to renew access token");
                    return;
                }

                String newAccessToken = refreshAccessToken(refreshToken);

                if (newAccessToken == null) {
                    unauthorized(response, "Failed to refresh access token");
                    return;
                }

                AuthSession.setAccessToken(newAccessToken);

                Preferences.userNodeForPackage(LegalContractDigitizerApplication.class).put("accessToken", newAccessToken);

                // Re-validate new access token
                tokenInfo = extractTokenInfo(newAccessToken);

                if (tokenInfo == null || !clientId.equals(tokenInfo.getAudience())) {
                    unauthorized(response, "Refreshed access token invalid or audience mismatch");
                    return;
                }
            }

            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(tokenInfo.getUserId(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Authentication failed", e);
            unauthorized(response, "Authentication error");
        }
    }

    private Tokeninfo extractTokenInfo(String accessToken) {
        try {
            Oauth2 oauth2 = new Oauth2.Builder(httpTransport, jsonFactory, null)
                    .setApplicationName(appName)
                    .build();

            return oauth2.tokeninfo().setAccessToken(accessToken).execute();
        } catch (IOException e) {
            log.debug("Failed to extract token info - token might be expired or invalid: {}", e.getMessage());
            return null;
        }
    }

    private String refreshAccessToken(String refreshToken) {
        try {
            GoogleRefreshTokenRequest request = new GoogleRefreshTokenRequest(httpTransport, jsonFactory, refreshToken, clientId, clientSecret);
            GoogleTokenResponse response = request.execute();
            return response.getAccessToken();
        } catch (IOException e) {
            log.error("Failed to refresh access token", e);
            return null;
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(message);
        response.getWriter().flush();
    }
}