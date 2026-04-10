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
                        // Actuator：健康检查供部署/探活（详情仍受 management.endpoint.health.show-details 控制）
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // swagger / openapi：本地联调与 Apifox 对照需要可访问
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        // 静态文件（头像等）公开读取
                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                        // 仅登录、注册公开；退出/当前用户/改密等需登录（见 JwtAuthenticationFilter）
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        // 填表：任务详情/下载/取消/重跑仅 publicId；列表需登录（非管理员也可看自己的历史）
                        .requestMatchers(
                                "/api/fill/tasks/public/**",
                                "/api/fill/download/public/**"
                        ).permitAll()
                        .requestMatchers(
                                "/api/fill/submit",
                                "/api/fill/free"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/fill/tasks").authenticated()
                        // 文档集：上传与 publicId 只读对匿名开放；删除/列表等需管理员
                        .requestMatchers("/api/documents/upload").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/sets/public/**").permitAll()
                        .requestMatchers("/api/documents/**").hasRole("ADMIN")
                        // 模板：上传与 publicId 只读对匿名开放；改删/列表等需管理员
                        .requestMatchers("/api/templates/upload").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/templates/public/**").permitAll()
                        .requestMatchers("/api/templates/**").hasRole("ADMIN")
                        // 报表类型：公共查询对外开放；写操作收口给管理员
                        .requestMatchers("/api/report-types", "/api/report-types/public/**").permitAll()
                        .requestMatchers("/api/report-types/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/report-types/**").hasRole("ADMIN")
                        // 开发调试接口仅管理员可访问
                        .requestMatchers("/api/dev/**").hasRole("ADMIN")
                        // 其他接口默认需要登录（目前基本没有）
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    /**
     * 开发期 CORS：允许本地前端（静态服务器）访问后端 API。
     * 如需部署到线上，请改为更严格的 allowlist（或交给网关处理）。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173"
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
