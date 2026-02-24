package org.bupt.demoapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())

                // 放行接口 - 不需要认证
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()  // 登录注册接口
                        .requestMatchers("/chat", "/chat/**").permitAll()  // 聊天接口（支持所有路径）
                        .anyRequest().permitAll()  // 暂时全部放行，方便调试
                );

        // 不配置 httpBasic，完全禁用 Basic 认证

        return http.build();
    }
}