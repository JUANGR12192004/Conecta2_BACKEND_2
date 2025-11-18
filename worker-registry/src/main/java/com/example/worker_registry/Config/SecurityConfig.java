package com.example.worker_registry.config;

import com.example.worker_registry.securtity.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .cors(cors -> {})
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/clients/services/public/**").permitAll()
                .requestMatchers("/api/v1/clients/services/**").hasRole("CLIENT")
                .requestMatchers("/api/v1/workers/services/**").hasRole("WORKER")
                .requestMatchers("/api/v1/clients/*/offers/pending").hasRole("CLIENT")
                .requestMatchers("/api/v1/workers/*/offers/pending").hasRole("WORKER")
                .requestMatchers("/api/v1/offers/*/respond").hasRole("CLIENT")
                .requestMatchers("/api/v1/offers/*/counter").hasRole("CLIENT")
                .requestMatchers("/payment/webhook").permitAll()
                .requestMatchers("/payment/**").hasRole("CLIENT")
                .requestMatchers("/api/v1/offers/*/worker/respond").hasRole("WORKER")
                .requestMatchers("/api/Clientes/**").permitAll()
                .requestMatchers("/api/Trabajadores/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
