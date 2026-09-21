package com.nxtime.nxtime.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación OpenAPI / Swagger UI (springdoc-openapi, en el
 * classpath desde la Fase 0). UI en {@code /swagger-ui.html}, spec cruda
 * en {@code /v3/api-docs} -- ambas rutas permitidas sin autenticar en
 * {@link SecurityConfig} (no forman parte de la API en sí, son
 * documentación).
 *
 * El esquema "bearerAuth" solo se declara aquí; qué endpoints lo exigen
 * se anota con {@code @SecurityRequirement("bearerAuth")} a nivel de
 * controlador en cada uno de los que vive bajo {@code /api/v1/**} -- los
 * de {@code /auth/**} son públicos y no lo llevan. Así el candado de
 * Swagger UI refleja la autorización real (ver {@code SecurityConfig})
 * en vez de aplicarse a ciegas a todos los endpoints por igual.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "NX Time API",
                version = "v1",
                description = "API REST del backend de NX Time: registro horario (fichaje), "
                        + "gestión de ausencias y administración de empleados, multi-tenant "
                        + "por empresa.",
                contact = @Contact(name = "NX Time")),
        /*
         * Los servidores, declarados (Fase C1).
         *
         * Sin esto springdoc pone el "Generated server url", que es la
         * dirección desde la que se pidió la spec. En docs/openapi.json quedó
         * congelado como http://localhost:8099 --el puerto de la máquina donde
         * se volcó aquella vez-- y cualquier cliente generado a partir de ese
         * fichero apuntaría a un puerto que no existe en ningún sitio.
         *
         * Producción primero: es lo que hay que elegir por defecto al abrir
         * Swagger UI desde el enlace del README.
         */
        servers = {
                @Server(url = "https://nxtime-backend.onrender.com", description = "Producción"),
                @Server(url = "http://localhost:8080", description = "Local (docker compose)")
        })
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token obtenido en /auth/login o /auth/register-manager "
                + "(cabecera Authorization: Bearer <token>).")
public class OpenApiConfig {
}
