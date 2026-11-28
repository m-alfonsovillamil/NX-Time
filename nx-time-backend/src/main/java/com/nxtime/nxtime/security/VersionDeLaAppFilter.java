package com.nxtime.nxtime.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Las versiones de la app que ya no se pueden usar (ADR 064).
 *
 * La app Android manda en cada petición {@value #CABECERA_VERSION} (su
 * {@code versionCode}) y {@value #CABECERA_PAQUETE} (su {@code applicationId}).
 * Si la versión es menor que {@code application.app.version-minima}, o el
 * paquete está en {@code application.app.ids-retirados} (la app de antes de
 * un cambio de nombre), responde <b>426</b> con {@code codigo} =
 * {@value #DESACTUALIZADA} o {@value #RETIRADA} y {@code urlDescarga}, y la app
 * enseña «Actualiza la app» o «Esta app ya no se usa».
 *
 * <p><b>Lo que sigue abierto siempre</b> ({@link #ABIERTAS}): entrar, renovar
 * la sesión, cerrarla y mandar los fichajes hechos sin conexión. Un fichaje
 * guardado en el móvil es un registro de jornada: que la app sea vieja no puede
 * hacer que se pierda.
 *
 * <p>Sin cabeceras (la web, el kiosco, curl) no hace nada: solo la app las
 * manda. Con la versión mínima a 0 y sin paquetes retirados, tampoco.
 */
@Component
public class VersionDeLaAppFilter extends OncePerRequestFilter {

    public static final String CABECERA_VERSION = "X-App-Version";
    public static final String CABECERA_PAQUETE = "X-App-Id";
    public static final String DESACTUALIZADA = "APP_DESACTUALIZADA";
    public static final String RETIRADA = "APP_RETIRADA";

    /** Lo que una app vieja puede seguir haciendo: prefijos de ruta. */
    static final List<String> ABIERTAS = List.of(
            "/auth/login",
            "/auth/refresh",
            "/auth/logout",
            "/api/v1/fichaje/sin-conexion",
            "/actuator/health");

    private final int versionMinima;
    private final Set<String> retirados;
    private final String urlDescarga;
    private final ObjectMapper objectMapper;

    public VersionDeLaAppFilter(
            @Value("${application.app.version-minima:0}") int versionMinima,
            @Value("${application.app.ids-retirados:}") String retirados,
            @Value("${application.app.download-url:}") String urlDescarga,
            ObjectMapper objectMapper) {
        this.versionMinima = versionMinima;
        this.retirados = Arrays.stream(retirados.split(","))
                .map(String::strip).filter(p -> !p.isEmpty()).collect(Collectors.toUnmodifiableSet());
        this.urlDescarga = urlDescarga == null ? "" : urlDescarga;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        Optional<String> motivo = abierta(request) ? Optional.empty() : motivo(
                request.getHeader(CABECERA_PAQUETE), request.getHeader(CABECERA_VERSION));
        if (motivo.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean retirada = RETIRADA.equals(motivo.get());
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UPGRADE_REQUIRED, retirada
                ? "Esta app ya no se usa: instala la nueva desde el enlace."
                : "Hay una versión nueva de la app y esta ya no funciona. Actualízala para seguir.");
        problema.setProperty("codigo", motivo.get());
        problema.setProperty("urlDescarga", urlDescarga);
        response.setStatus(HttpStatus.UPGRADE_REQUIRED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problema);
    }

    /** Por qué no se atiende a esta app, o vacío si se atiende. Package-private para los tests. */
    Optional<String> motivo(String paquete, String version) {
        if (paquete != null && retirados.contains(paquete.strip().replaceFirst("\\.dev$", ""))) {
            return Optional.of(RETIRADA);
        }
        if (versionMinima > 0 && version != null) {
            try {
                if (Integer.parseInt(version.strip()) < versionMinima) {
                    return Optional.of(DESACTUALIZADA);
                }
            } catch (NumberFormatException e) {
                // Una cabecera que no es un número no la manda la app: se ignora.
            }
        }
        return Optional.empty();
    }

    private static boolean abierta(HttpServletRequest request) {
        String ruta = request.getServletPath();
        return ABIERTAS.stream().anyMatch(ruta::startsWith);
    }
}
