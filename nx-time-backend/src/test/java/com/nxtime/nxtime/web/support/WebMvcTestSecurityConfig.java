package com.nxtime.nxtime.web.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de seguridad mínima para los {@code @WebMvcTest} de los
 * controladores.
 *
 * A propósito NO reutiliza la {@code SecurityConfig} real de producción:
 * esa configuración arrastra el filtro JWT, el rate limiter y CORS, que
 * son infraestructura ajena a lo que un test de controlador debe
 * verificar (eso ya lo cubre {@code ApiContractTest} contra la app
 * completa). Aquí solo hace falta lo mínimo para que {@code @PreAuthorize}
 * se evalúe de verdad contra las authorities que ponga
 * {@code @WithMockUser}/{@code @WithMockSecurityUser} en cada test: rutas
 * de /auth públicas (igual que en producción) y autenticación obligatoria
 * para el resto.
 */
@TestConfiguration
@EnableMethodSecurity
public class WebMvcTestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
