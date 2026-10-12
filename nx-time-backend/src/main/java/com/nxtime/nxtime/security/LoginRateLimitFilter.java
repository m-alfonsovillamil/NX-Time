package com.nxtime.nxtime.security;

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
import tools.jackson.databind.ObjectMapper;

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
 * ver {@link #peticionesPorMinuto}; entrar va aparte, ver abajo), en memoria
 * (un Map, no Redis):
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
 *
 * <h2>Entrar tiene su propio cupo (octubre de 2026, ADR 041)</h2>
 *
 * Con un solo cupo de diez para todo, <b>una oficina de más de diez personas
 * no podía entrar a la vez</b>: salen todas a internet por la misma IP. En la
 * prueba de carga, de cuarenta personas entrando desde una IP, treinta se
 * llevaron un 429 (docs/PRUEBA-DE-CARGA.md).
 *
 * Así que hay dos cupos por IP, que no se gastan el uno al otro:
 *
 * <ul>
 *   <li><b>Entrar</b> ({@link #RUTAS_DE_ENTRADA}: el login y las vueltas del
 *       SSO): sesenta por minuto. Es lo que hace toda una plantilla a las
 *       nueve, y una por segundo es además lo que aguanta la CPU del plan
 *       gratuito sin hundirse.</li>
 *   <li><b>Lo demás</b> (registrar una empresa, pedir y canjear códigos,
 *       emparejar un kiosco): las diez de siempre. Varias mandan correos, y
 *       nadie legítimo las repite.</li>
 * </ul>
 *
 * Contra una cuenta concreta sigue frenando {@link LimitadorDeIntentosPorCuenta},
 * venga de la IP que venga. Lo que este límite sigue parando es a quien prueba
 * una contraseña corriente contra muchas cuentas desde un sitio, y a quien
 * quiere gastar la CPU a base de entradas.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    /**
     * Entrar, con contraseña o con una cuenta de fuera. Su cupo es aparte.
     *
     * El SSO (ADR 036) va aquí porque es entrar, y a una oficina que entra con
     * Google le pasa lo mismo que a una que entra con contraseña. La vuelta
     * del proveedor hace que este servidor llame al suyo para canjear el
     * código: por eso tiene límite, para que nadie con una cookie de estado
     * (que se consigue con solo empezar) lo use para machacarlo. Empezar no se
     * limita: es una redirección y no cuesta nada.
     */
    private static final Set<String> RUTAS_DE_ENTRADA = Set.of(
            "/auth/login", "/auth/sso/google/vuelta", "/auth/sso/microsoft/vuelta", "/auth/sso/canjear");

    private static final Set<String> RUTAS_LIMITADAS = Set.of(
            "/auth/register-manager", "/auth/registro/confirmar", "/auth/recuperar",
            "/auth/recuperar/confirmar",
            // Pedir un código para emparejar un kiosco es público (ADR 033). Preguntar
            // por su estado no se limita: la tablet lo hace cada pocos segundos, y va
            // con un secreto de 256 bits que no se puede adivinar a base de probar.
            "/kiosco/emparejar",
            // Registrar una empresa con una cuenta de fuera (ADR 038) crea una
            // empresa, como /auth/register-manager.
            "/auth/sso/registro");
    static final int PETICIONES_POR_MINUTO = 10;
    static final int ENTRADAS_POR_MINUTO = 60;

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

    /** Cuántas entradas por minuto y por IP: ver arriba, «Entrar tiene su propio cupo». */
    private final int entradasPorMinuto;

    public LoginRateLimitFilter(ObjectMapper objectMapper, int proxiesDeConfianza) {
        this(objectMapper, proxiesDeConfianza, PETICIONES_POR_MINUTO, ENTRADAS_POR_MINUTO);
    }

    @Autowired
    public LoginRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${application.security.rate-limit.trusted-proxies:1}") int proxiesDeConfianza,
            @Value("${application.security.rate-limit.peticiones-por-minuto:" + PETICIONES_POR_MINUTO + "}") int peticionesPorMinuto,
            @Value("${application.security.rate-limit.entradas-por-minuto:" + ENTRADAS_POR_MINUTO + "}") int entradasPorMinuto) {
        this.objectMapper = objectMapper;
        this.proxiesDeConfianza = Math.max(0, proxiesDeConfianza);
        this.peticionesPorMinuto = Math.max(1, peticionesPorMinuto);
        this.entradasPorMinuto = Math.max(1, entradasPorMinuto);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        boolean esEntrar = RUTAS_DE_ENTRADA.contains(request.getServletPath());
        if (!esEntrar && !RUTAS_LIMITADAS.contains(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Un cupo por IP para entrar y otro para lo demás: la clave los separa.
        int porMinuto = esEntrar ? entradasPorMinuto : peticionesPorMinuto;
        Bucket bucket = buckets.get((esEntrar ? "entrar:" : "resto:") + clientIp(request), clave -> nuevoBucket(porMinuto));

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

    private static Bucket nuevoBucket(int porMinuto) {
        Bandwidth limite = Bandwidth.builder().capacity(porMinuto).refillGreedy(porMinuto, Duration.ofMinutes(1)).build();
        return Bucket.builder().addLimit(limite).build();
    }

    /** La IP en la que se apoya el límite, contando desde el final: ver {@link IpDelCliente}. */
    private String clientIp(HttpServletRequest request) {
        return IpDelCliente.de(request, proxiesDeConfianza);
    }
}
