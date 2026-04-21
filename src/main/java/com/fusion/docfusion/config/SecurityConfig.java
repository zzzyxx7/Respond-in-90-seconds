package com.fusion.docfusion.config;

import com.fusion.docfusion.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()

                        .requestMatchers("/api/fill/tasks/public/**", "/api/fill/download/public/**").permitAll()
                        .requestMatchers("/api/fill/submit", "/api/fill/free").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/fill/tasks").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/fill/tasks/token-stats").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/fill/tasks/sync").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/documents/upload").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/documents/sets/public/*/append").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/sets/public/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/documents/sets/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/sets").authenticated()
                        .requestMatchers("/api/documents/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/templates/upload").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/templates/sync").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/templates/list", "/api/templates/by-report-type").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/templates/download/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/templates/public/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/templates/public/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/templates/public/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/templates/public/**").permitAll()
                        .requestMatchers("/api/templates/**").hasRole("ADMIN")

                        .requestMatchers("/api/report-types", "/api/report-types/public/**").permitAll()
                        .requestMatchers("/api/report-types/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/report-types/**").hasRole("ADMIN")

                        .requestMatchers("/api/dev/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5500",
                "http://127.0.0.1:5500"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
