package com.fusion.docfusion.config;

import com.fusion.docfusion.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
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
                        // 登录与认证相关接口本身公开
                        .requestMatchers("/api/auth/**").permitAll()
                        // 核心业务接口对匿名用户也开放（提交填表、查询任务、下载结果等）
                        .requestMatchers("/api/fill/**").permitAll()
                        .requestMatchers("/api/documents/**").permitAll()
                        .requestMatchers("/api/templates/**").permitAll()
                        .requestMatchers("/api/report-types/**").permitAll()
                        // 开发调试接口仅管理员可访问
                        .requestMatchers("/api/dev/**").hasRole("ADMIN")
                        // 其他接口默认需要登录（目前基本没有）
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
