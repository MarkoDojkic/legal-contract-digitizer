package dev.markodojkic.legalcontractdigitizer.config;

import dev.markodojkic.legalcontractdigitizer.util.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security configuration class that sets up HTTP security, including CORS configuration,
 * JWT authentication filter, and endpoint access rules.
 *
 * <p>Key features:
 * <ul>
 *   <li>Defines allowed CORS origins, methods, and headers, enabling credentials support.</li>
 *   <li>Registers a JWT authentication filter to validate tokens before username/password authentication.</li>
 *   <li>Configures endpoint access rules:
 *     <ul>
 *       <li>Allows unauthenticated access to Swagger UI and related API documentation resources.</li>
 *       <li>Requires authentication for API endpoints under "/api/**".</li>
 *       <li>Denies access to all other requests.</li>
 *     </ul>
 *   </li>
 *   <li>Disables HTTP basic authentication, form login, and CSRF protection.</li>
 *   <li>Sets session management to migrate session on fixation and create sessions if required.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${server.port}")
    private Integer serverPort;

    /**
     * Configures CORS settings allowing requests from localhost on the configured server port,
     * with specified HTTP methods and headers, and allowing credentials.
     *
     * @return the CORS configuration source bean
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:" + serverPort
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Configures the Spring Security filter chain, including disabling default HTTP basic
     * and form login, adding the JWT authentication filter, setting authorization rules,
     * session management policies, CSRF, and CORS.
     *
     * @param http the HttpSecurity builder
     * @return configured SecurityFilterChain instance
     * @throws Exception in case of configuration errors
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/swagger-ui.html",
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .sessionManagement(session -> session
                        .sessionFixation().migrateSession()
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .build();
    }

    /**
     * Instantiates the JWT authentication filter bean that intercepts requests
     * to validate JWT tokens.
     *
     * @return new JwtAuthenticationFilter instance
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
}