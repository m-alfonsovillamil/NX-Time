package com.nxtime.nxtime.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita los intentos a /auth/login, /auth/register-manager y los códigos
 * de acceso por IP: antes la fuerza bruta contra el login era libre (ver
 * auditoría, defectos de diseño), y /auth/register-manager es público --
 * cualquiera en internet puede crear una empresa nueva sin ningún control
 * (ver plan, Fase 4: "decisión consciente" documentada en ese punto en vez
 * de cerrarlo del todo, ya que aún no hay verificación por email --
 * Fase 10 -- ni códigos de invitación).
 *
 * Desde el 09/2026 también /auth/recuperar y /auth/recuperar/confirmar
 * (ADR 014). Este límite frena a quien prueba códigos contra muchas
 * cuentas desde una IP; contra una cuenta concreta lo frenan los 5
 * intentos de cada código y los 3 códigos por hora.
 *
 * 10 peticiones por minuto y por IP, en memoria (un Map, no Redis):
 * suficiente para un servicio con una sola instancia como este. Si
 * algún día corre en varias instancias a la vez, cada una tendría su
 * propio contador -- limitación conocida, aceptable para el alcance de
 * este proyecto.
 *
 * 🚨 De dónde sale la IP importa tanto como el límite. Ver {@link #clientIp}:
 * hasta el 19/09/2026 se cogía el PRIMER valor de X-Forwarded-For, que lo
 * pone quien llama, y bastaba con mandar una IP inventada distinta en cada
 * intento para tener un contador nuevo cada vez. Comprobado contra
 * producción: sin cabecera el 429 llegaba al intento 11; con una IP falsa
 * por intento, quince intentos y ningún 429.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RUTAS_LIMITADAS = Set.of(
            "/auth/login", "/auth/register-manager", "/auth/recuperar", "/auth/recuperar/confirmar");
    private static final int PETICIONES_POR_MINUTO = 10;

    /**
     * Acotado a propósito: era un Map que crecía sin límite, con una entrada
     * por cada IP vista y sin que nadie las quitara nunca. Quien quisiera
     * podía ir llenándolo cambiando de IP en cada intento -- que es
     * justamente lo que hacía falta para esquivar el límite.
     */
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(5))
            .build();
    private final ObjectMapper objectMapper;

    /**
     * Cuántos proxies de confianza hay delante de la aplicación.
     *
     * En Render es uno: su balanceador. Si algún día se pone algo delante
     * (Cloudflare, otro balanceador), hay que subirlo, porque cada salto
     * añade una entrada a X-Forwarded-For. Quedarse corto hace que todo el
     * tráfico comparta el contador del proxy y se limiten unos a otros;
     * pasarse devuelve a confiar en lo que manda el cliente.
     */
    private final int proxiesDeConfianza;

    public LoginRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${application.security.rate-limit.trusted-proxies:1}") int proxiesDeConfianza) {
        this.objectMapper = objectMapper;
        this.proxiesDeConfianza = Math.max(0, proxiesDeConfianza);
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

        Bucket bucket = buckets.get(clientIp(request), ip -> nuevoBucket());

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

    /**
     * La IP en la que se apoya el límite, contando desde el final.
     *
     * X-Forwarded-For se lee de izquierda a derecha como "quien llamó
     * primero, y luego cada proxy por el que pasó". El PRIMER valor lo
     * escribe el cliente y por lo tanto se lo puede inventar; cada proxy
     * AÑADE al final la dirección que él ha visto. La única entrada en la
     * que se puede confiar es la que puso el último proxy de confianza, así
     * que se cuenta desde el final tantas posiciones como proxies haya.
     *
     * Con un proxy delante (Render) y la cabecera
     * "1.2.3.4, 198.51.100.7", la buena es 198.51.100.7 -- la que vio
     * Render --, no la 1.2.3.4 que mandó quien llamaba.
     *
     * Si la cabecera trae menos entradas de las que deberia, se usa
     * getRemoteAddr(): es la del salto inmediato y no se puede falsificar.
     */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank() && proxiesDeConfianza > 0) {
            String[] saltos = forwardedFor.split(",");
            int posicion = saltos.length - proxiesDeConfianza;
            if (posicion >= 0 && posicion < saltos.length) {
                String ip = saltos[posicion].trim();
                if (!ip.isEmpty()) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
