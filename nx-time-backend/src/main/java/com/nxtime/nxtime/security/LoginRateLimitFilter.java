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
import org.springframework.beans.factory.annotation.Autowired;
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
 * 10 peticiones por minuto y por IP (configurable solo para el perfil dev,
 * ver {@link #peticionesPorMinuto}), en memoria (un Map, no Redis):
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
            "/auth/login", "/auth/register-manager", "/auth/registro/confirmar", "/auth/recuperar",
            "/auth/recuperar/confirmar",
            // Pedir un código para emparejar un kiosco es público (ADR 033). Preguntar
            // por su estado no se limita: la tablet lo hace cada pocos segundos, y va
            // con un secreto de 256 bits que no se puede adivinar a base de probar.
            "/kiosco/emparejar");
    static final int PETICIONES_POR_MINUTO = 10;

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

    /**
     * Cuántas peticiones por minuto y por IP. Las 10 de siempre salvo que se
     * diga otra cosa, y solo el perfil {@code dev} dice otra cosa: la suite
     * de Playwright entra y sale una veintena de veces en medio minuto desde
     * la misma IP, y con 10 fallaba por el límite y no por la web.
     */
    private final int peticionesPorMinuto;

    public LoginRateLimitFilter(ObjectMapper objectMapper, int proxiesDeConfianza) {
        this(objectMapper, proxiesDeConfianza, PETICIONES_POR_MINUTO);
    }

    @Autowired
    public LoginRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${application.security.rate-limit.trusted-proxies:1}") int proxiesDeConfianza,
            @Value("${application.security.rate-limit.peticiones-por-minuto:" + PETICIONES_POR_MINUTO + "}") int peticionesPorMinuto) {
        this.objectMapper = objectMapper;
        this.proxiesDeConfianza = Math.max(0, proxiesDeConfianza);
        this.peticionesPorMinuto = Math.max(1, peticionesPorMinuto);
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
        Bandwidth limite = Bandwidth.simple(peticionesPorMinuto, Duration.ofMinutes(1));
        return Bucket.builder().addLimit(limite).build();
    }

    /** La IP en la que se apoya el límite, contando desde el final: ver {@link IpDelCliente}. */
    private String clientIp(HttpServletRequest request) {
        return IpDelCliente.de(request, proxiesDeConfianza);
    }
}
