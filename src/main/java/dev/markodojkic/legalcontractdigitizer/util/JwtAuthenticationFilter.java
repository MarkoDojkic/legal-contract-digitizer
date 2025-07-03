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
import dev.markodojkic.legalcontractdigitizer.exception.UnauthorizedAccessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
 * <p>
 * It intercepts incoming requests, checks for a Bearer token, and verifies it against Google's token info endpoint.
 * If the token is expired but a valid refresh token is available, it will refresh the access token.
 */
@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${spring.application.name}")
    private String appName;

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    @Value("${google.tokenUrl}")
    private String googleTokenUrl;

    @Value("${google.tokenInfoUrl}")
    private String googleTokenInfoUrl;

    private final HttpTransport httpTransport = new NetHttpTransport();
    private final JsonFactory jsonFactory = new GsonFactory();

    /**
     * Filters each incoming request and validates the Authorization header.
     *
     * @param request     the HTTP request
     * @param response    the HTTP response
     * @param filterChain the filter chain
     * @throws ServletException in case of a servlet exception
     * @throws IOException      in case of an I/O error
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);

            try {
                Tokeninfo tokeninfo = extractTokenInfo(accessToken);

                if (tokeninfo == null || !tokeninfo.getAudience().equals(clientId)) {
                    throw new UnauthorizedAccessException("Access token is not for this application");
                } else if (tokeninfo.getExpiresIn() == 0) {
                    String refreshToken = AuthSession.getRefreshToken();

                    if (refreshToken != null && !refreshToken.isEmpty()) {
                        String newAccessToken = refreshAccessToken(refreshToken);

                        if (newAccessToken != null) {
                            AuthSession.setAccessToken(newAccessToken);
                            Preferences.userNodeForPackage(LegalContractDigitizerApplication.class)
                                    .put("accessToken", newAccessToken);
                            tokeninfo = extractTokenInfo(newAccessToken);

                            if (tokeninfo == null || tokeninfo.getExpiresIn() == 0) {
                                throw new UnauthorizedAccessException("Refreshed access token is expired");
                            }
                        } else {
                            throw new UnauthorizedAccessException("Access token and refresh access token are invalid");
                        }
                    } else {
                        throw new UnauthorizedAccessException("Refresh access token is invalid");
                    }
                }

                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                tokeninfo.getUserId(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )
                );
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts token information by calling Google's token info endpoint.
     *
     * @param accessToken the access token to validate
     * @return the Tokeninfo object if valid, otherwise null
     * @throws IOException if the token info endpoint call fails
     */
    private Tokeninfo extractTokenInfo(String accessToken) throws IOException {
        try {
            Oauth2 oauth2 = new Oauth2.Builder(httpTransport, jsonFactory, null)
                    .setApplicationName(appName)
                    .build();

            return oauth2.tokeninfo().setAccessToken(accessToken).execute();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Uses a refresh token to obtain a new access token from Google's token endpoint.
     *
     * @param refreshToken the refresh token
     * @return a new access token string, or null if the request fails
     */
    private String refreshAccessToken(String refreshToken) {
        try {
            GoogleRefreshTokenRequest refreshTokenRequest = new GoogleRefreshTokenRequest(
                    httpTransport,
                    jsonFactory,
                    refreshToken,
                    clientId,
                    clientSecret
            );

            GoogleTokenResponse tokenResponse = refreshTokenRequest.execute();
            return tokenResponse.getAccessToken();
        } catch (IOException e) {
            return null;
        }
    }
}