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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Un tope de peticiones por cuenta para lo que cuesta (ADR 039): informes,
 * analítica, la exportación de mis datos y la comprobación de integridad.
 *
 * Hasta aquí solo tenía límite lo que se puede pedir sin sesión
 * ({@link LoginRateLimitFilter}). Con sesión no había ninguno, y tener sesión
 * no es ninguna barrera: registrar una empresa es público, y quien la registra
 * es su ADMIN. Con eso se podía pedir en bucle lo más caro que hay --cada
 * petición, un hilo y una conexión del pool mientras dura-- y dejar sin
 * conexiones al resto.
 *
 * <b>Por cuenta y no por IP</b>: aquí ya se sabe quién pide, y una oficina
 * entera detrás de una sola IP no tiene por qué compartir el cupo.
 *
 * <b>Solo lo caro.</b> Fichar, el panel o los listados van sin tope a
 * propósito: son lo que la gente usa todo el día, y un límite ahí es más fácil
 * que moleste a quien trabaja que a quien ataca.
 *
 * Va DESPUÉS del filtro del JWT (ver {@code SecurityConfig}): necesita saber
 * de quién es la petición. A quien llega sin sesión no le hace nada; ya le
 * responderá un 401 más adelante.
 *
 * Como el otro, vive en la memoria de esta instancia: con varias, el tope real
 * es este multiplicado por las que haya. Está anotado en el ADR.
 */
@Component
public class LimiteDeLoCaro extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LimiteDeLoCaro.class);

    /**
     * Treinta por minuto y cuenta. La pantalla de analítica pide tres cosas a
     * la vez, así que son diez visitas por minuto: de sobra para quien mira y
     * muy poco para quien quiere tumbar el servicio.
     */
    static final int POR_MINUTO = 30;

    /** Lo que se limita. Con la barra final donde hay más rutas debajo. */
    private static final List<String> RUTAS_CARAS = List.of(
            "/api/v1/informes/",
            "/api/v1/analitica/",
            "/api/v1/perfil/mis-datos",
            "/api/v1/auditoria/integridad",
            "/api/v1/plataforma/");

    /** Acotado, como el del login: una entrada por cuenta vista, y se van solas. */
    private final Cache<String, Bucket> cupos = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(5))
            .build();

    /** A quién se le ha apuntado ya en el log que ha llegado al tope, durante un minuto. */
    private final Cache<String, Boolean> avisados = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    private final ObjectMapper objectMapper;
    private final int porMinuto;

    public LimiteDeLoCaro(
            ObjectMapper objectMapper,
            @Value("${application.security.rate-limit.caro-por-minuto:" + POR_MINUTO + "}") int porMinuto) {
        this.objectMapper = objectMapper;
        this.porMinuto = Math.max(1, porMinuto);
    }

    static boolean esCara(String ruta) {
        return ruta != null && RUTAS_CARAS.stream().anyMatch(ruta::startsWith);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !esCara(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication quien = SecurityContextHolder.getContext().getAuthentication();
        if (quien == null || !quien.isAuthenticated() || quien instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket cupo = cupos.get(quien.getName(), cuenta -> nuevoCupo());
        if (cupo.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Una vez por cuenta y minuto, no una por petición rechazada: midiendo
        // esto, treinta peticiones en bucle dejaron 26.000 líneas en quince
        // segundos. Un aviso que llena el log durante un ataque es parte del
        // ataque. Y sin el correo: la ruta basta para ver qué se pide de más.
        if (avisados.asMap().putIfAbsent(quien.getName(), Boolean.TRUE) == null) {
            log.warn("Límite de peticiones caras alcanzado en {}.", request.getServletPath());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Has pedido esto demasiadas veces seguidas. Espera un minuto y vuelve a intentarlo.");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }

    private Bucket nuevoCupo() {
        Bandwidth limite = Bandwidth.builder().capacity(porMinuto).refillGreedy(porMinuto, Duration.ofMinutes(1)).build();
        return Bucket.builder().addLimit(limite).build();
    }
}
