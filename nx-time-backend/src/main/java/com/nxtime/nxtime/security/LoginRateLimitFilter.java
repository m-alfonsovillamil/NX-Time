package com.nxtime.nxtime.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita los intentos a /auth/login y /auth/register-manager por IP:
 * antes la fuerza bruta contra el login era libre (ver auditoría,
 * defectos de diseño), y /auth/register-manager es público -- cualquiera
 * en internet puede crear una empresa nueva sin ningún control (ver
 * plan, Fase 4: "decisión consciente" documentada en ese punto en vez
 * de cerrarlo del todo, ya que aún no hay verificación por email --
 * Fase 10 -- ni códigos de invitación).
 *
 * 10 peticiones por minuto y por IP, en memoria (un Map, no Redis):
 * suficiente para un servicio con una sola instancia como este. Si
 * algún día corre en varias instancias a la vez, cada una tendría su
 * propio contador -- limitación conocida, aceptable para el alcance de
 * este proyecto.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RUTAS_LIMITADAS = Set.of("/auth/login", "/auth/register-manager");
    private static final int PETICIONES_POR_MINUTO = 10;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public LoginRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!RUTAS_LIMITADAS.contains(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(clientIp(request), ip -> nuevoBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Inténtalo de nuevo en un minuto.");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }

    private Bucket nuevoBucket() {
        Bandwidth limite = Bandwidth.simple(PETICIONES_POR_MINUTO, Duration.ofMinutes(1));
        return Bucket.builder().addLimit(limite).build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
