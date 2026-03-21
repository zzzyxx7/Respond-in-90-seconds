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
                        // 登录公开
                        .requestMatchers("/api/auth/**").permitAll()
                        // 开发调试接口仅管理员可访问
                        .requestMatchers("/api/dev/**").hasRole("ADMIN")
                        // 只保留少量公开读取：报表类型（不涉及用户数据隔离）
                        .requestMatchers("/api/report-types/**").permitAll()
                        // 文档/模板/填表都属于用户隔离数据：要求登录
                        .requestMatchers("/api/documents/**").authenticated()
                        .requestMatchers("/api/templates/**").authenticated()
                        // 提交/查询任务/下载结果都属于消耗或敏感数据：要求登录
                        .requestMatchers("/api/fill/**").authenticated()
                        // 其他接口默认需要登录（目前基本没有）
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
