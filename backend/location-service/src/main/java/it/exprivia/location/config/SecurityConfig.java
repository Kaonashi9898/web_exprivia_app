package it.exprivia.location.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.exprivia.location.exception.ApiErrorResponse;
import it.exprivia.location.security.JwtAuthFilter;
import it.exprivia.location.security.JwtUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtUtils);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, authException) ->
                            writeError(response, HttpStatus.UNAUTHORIZED, "Autenticazione richiesta"))
                    .accessDeniedHandler((request, response, accessDeniedException) ->
                            writeError(response, HttpStatus.FORBIDDEN, "Permessi insufficienti"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Regole specifiche PRIMA di quella generica GET (Spring Security: vince la prima che fa match)
                .requestMatchers(HttpMethod.GET, "/api/gruppi-postazioni/postazione/**")
                    .hasAnyRole("USER", "RECEPTION", "BUILDING_MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/gruppi-postazioni/piano/**")
                    .hasAnyRole("USER", "RECEPTION", "BUILDING_MANAGER", "ADMIN")
                .requestMatchers("/api/gruppi-postazioni/**").hasAnyRole("BUILDING_MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/piani/*/planimetria/**").hasAnyRole("BUILDING_MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/piani/*/planimetria", "/api/piani/*/planimetria/**").hasAnyRole("BUILDING_MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/postazioni/**").hasAnyRole("BUILDING_MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/postazioni/**").hasAnyRole("BUILDING_MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/postazioni/**").hasAnyRole("BUILDING_MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/postazioni/**").hasAnyRole("BUILDING_MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/stanze/**").hasAnyRole("BUILDING_MANAGER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/sedi/**", "/api/edifici/**", "/api/piani/**", "/api/stanze/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/sedi/**", "/api/edifici/**", "/api/piani/**", "/api/stanze/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/sedi/**", "/api/edifici/**", "/api/piani/**", "/api/stanze/**").hasRole("ADMIN")
                // Regola generica: qualsiasi GET autenticato (dopo quelle specifiche)
                .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:4200",
                "http://localhost:4201",
                "http://localhost:8081",
                "http://localhost:8082",
                "http://localhost:8083",
                "http://localhost:13010",
                "http://localhost:13020",
                "http://localhost:13030",
                "http://localhost:13040",
                "http://localhost:13050"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(status, message));
    }
}
