package com.nxtime.nxtime.config;

import com.nxtime.nxtime.security.JwtAuthenticationFilter;
import com.nxtime.nxtime.security.LoginRateLimitFilter;
import com.nxtime.nxtime.security.RestAccessDeniedHandler;
import com.nxtime.nxtime.security.RestAuthenticationEntryPoint;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final Environment entorno;

    // Lista blanca de orígenes CORS, por perfil (ver application-dev.yml
    // / application-prod.yml), como cadena separada por comas -- @Value
    // no liga directamente una propiedad de texto a List<String>, así
    // que se parsea a mano en corsConfigurationSource(). Antes era "*"
    // fijo en el código -- ver auditoría, defectos de diseño.
    @Value("${application.security.cors.allowed-origins}")
    private String corsAllowedOriginsRaw;

    public SecurityConfig(
            AuthenticationProvider authenticationProvider,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            LoginRateLimitFilter loginRateLimitFilter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            Environment entorno
    ) {
        this.authenticationProvider = authenticationProvider;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.loginRateLimitFilter = loginRateLimitFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.entorno = entorno;
    }

    /**
     * La documentación, con su propia CSP (Fase A10).
     *
     * Swagger UI se sirve desde esta misma aplicación y necesita cargar sus
     * scripts y estilos, así que la CSP estricta de la cadena principal
     * --{@code default-src 'none'}-- la deja en blanco. Es la regresión clásica
     * al añadir cabeceras de seguridad a una API que además publica su
     * documentación: se aprieta la política pensando en JSON y se rompe la
     * única parte que devuelve HTML.
     *
     * Va con {@code @Order(1)} para que gane a la cadena de abajo, y su
     * securityMatcher son exactamente las rutas que ya eran públicas.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain documentacionFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        // Swagger UI inyecta estilos y un script de
                                        // arranque en la propia página.
                                        + "script-src 'self' 'unsafe-inline'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                // Cabeceras de seguridad (Fase A10). Hasta ahora no había
                // ninguna configurada: regían los valores por defecto de Spring
                // Security (nosniff, X-Frame-Options, Cache-Control), que están
                // bien pero no incluyen ni CSP ni HSTS. Con la API consumida
                // solo desde Android daba igual; en cuanto la consuma un
                // navegador, no.
                .headers(headers -> headers
                        // Esto devuelve JSON, nunca HTML: no debe poder cargar
                        // nada ni ser embebido en ninguna parte. Es la política
                        // más estricta posible, y aquí no cuesta nada.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        // Un año, con subdominios. Render sirve solo por HTTPS.
                        //
                        // El requestMatcher no es cosmético. Spring Security
                        // emite HSTS solo cuando request.isSecure(), y detrás
                        // del proxy de Render la petición llega a Tomcat por
                        // HTTP: sin esto, la cabecera NO saldría en producción,
                        // que es justo donde hace falta. La alternativa
                        // --activar server.forward-headers-strategy-- se
                        // descartó a propósito: ForwardedHeaderFilter toma el
                        // PRIMER valor de X-Forwarded-For, que es el
                        // falsificable, y reintroduciría por la puerta de atrás
                        // el agujero de rate limiting que se cerró el
                        // 19/09/2026 (ver LoginRateLimitFilter.clientIp).
                        //
                        // Emitirla siempre no tiene coste: por especificación,
                        // un navegador ignora HSTS recibida por HTTP. Y el
                        // navegador de un usuario real habla HTTPS con Render,
                        // así que para él la cabecera llega por una conexión
                        // segura y la respeta.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .requestMatcher(AnyRequestMatcher.INSTANCE)
                                .includeSubDomains(true)
                                .maxAgeInSeconds(Duration.ofDays(365).toSeconds()))
                        // La URL de la API puede llevar ids en la ruta; no hay
                        // motivo para mandárselos a nadie como referrer.
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()"))
                        .frameOptions(frame -> frame.deny()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        // La documentación (Fase 6) ya no se lista aquí: desde la Fase A10
                        // tiene su propia cadena de filtros, arriba, porque necesita una
                        // CSP distinta. Sigue siendo pública por la misma razón de
                        // siempre -- Swagger UI y la spec cruda no son parte de la API, no
                        // tiene sentido exigir un token para leerlas.
                        // Health check (Fase 7): lo usan Docker/Render para saber si el
                        // contenedor está vivo, antes de que exista ningún token posible.
                        .requestMatchers("/actuator/health").permitAll()
                        // Estado de las tareas nocturnas (paso 5 del piloto): lo consulta
                        // un workflow de GitHub sin sesión. No lleva datos de ninguna
                        // empresa, solo si cada tarea corrió y cómo acabó.
                        .requestMatchers("/estado/tareas").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origenesPermitidos());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Accept y Accept-Language las manda el navegador solo en cuanto se usa
        // fetch con cabeceras; X-Request-Id la entiende CorrelationIdFilter
        // desde hace tiempo, pero al no estar aquí el preflight la rechazaba y
        // la petición no llegaba a salir.
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Accept-Language", "X-Request-Id"));

        // Lo que el navegador puede LEER de la respuesta, que no es lo mismo
        // que lo que recibe. Sin declararlas aquí, el JavaScript no las ve
        // aunque vengan:
        //  - Content-Disposition: sin ella, una descarga de informe o adjunto
        //    pierde el nombre del fichero y se guarda como "download".
        //  - X-Request-Id: la que genera CorrelationIdFilter, para poder citar
        //    una petición concreta al reportar un fallo.
        //  - Retry-After: la que acompaña al 429 del rate limit, para poder
        //    decir cuánto hay que esperar en vez de "inténtalo más tarde".
        configuration.setExposedHeaders(List.of("Content-Disposition", "X-Request-Id", "Retry-After"));

        // Un preflight por hora y por combinación, en vez de uno por petición.
        // Con el arranque en frío de Render --medido en 160 s-- duplicar las
        // peticiones no es un detalle de latencia.
        configuration.setMaxAge(Duration.ofHours(1));

        // Sin credenciales: los tokens van en la cabecera Authorization, no en
        // cookies (ver ADR 024 cuando exista). Es también lo que hace correcto
        // tener CSRF desactivado, y lo que permite usar "*" en desarrollo.
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> origenesPermitidos() {
        return Arrays.stream(corsAllowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    /**
     * Avisa al arrancar si en producción no hay ningún origen permitido.
     *
     * Hoy es el caso: {@code CORS_ALLOWED_ORIGINS} está declarada en
     * render.yaml pero sin valor, así que cualquier página que intente hablar
     * con esta API desde un navegador recibe un error de CORS **en la consola
     * del navegador y en ningún sitio más** -- en los logs del backend no
     * aparece nada, porque la petición ni siquiera llega. Es el fallo que más
     * tiempo hace perder al desplegar un frontend por primera vez.
     *
     * Avisa y no impide arrancar: la app Android funciona perfectamente sin
     * ningún origen permitido, y tumbar producción por algo que hoy no se usa
     * sería peor que el problema.
     */
    @PostConstruct
    void avisarSiFaltanOrigenesEnProduccion() {
        if (entorno.acceptsProfiles(Profiles.of("prod")) && origenesPermitidos().isEmpty()) {
            log.warn("CORS sin orígenes permitidos en producción: ningún navegador podrá usar esta API. "
                    + "Si hay un frontend desplegado, hay que poner CORS_ALLOWED_ORIGINS en Render.");
        }
    }
}
