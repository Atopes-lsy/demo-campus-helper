package com.campus.helper.config;

import com.campus.helper.common.Result;
import com.campus.helper.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**", "/ws", "/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/runners", "/api/payments/callback").permitAll()
                        .requestMatchers("/api/bi/**", "/api/statistics/**", "/api/ai/**",
                                "/api/search/**", "/api/students/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payments").hasRole("STUDENT")
                        // Manual dispatch retry. New orders are auto-dispatched by
                        // DispatchConsumer, so this is an operator action rather than a student's.
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/dispatch").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/runners/**").hasRole("ADMIN")
                        // The GET and PUT rules only cover those methods, so revoking a runner's
                        // sessions needs its own entry — otherwise any authenticated user could
                        // invalidate someone else's tokens.
                        .requestMatchers(HttpMethod.POST, "/api/runners/*/revoke-tokens").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/runners", "/api/runners/top").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, 401, "请先登录或登录已过期"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, 403, "无权执行此操作")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response,
                            int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.error(status, message));
    }
}
