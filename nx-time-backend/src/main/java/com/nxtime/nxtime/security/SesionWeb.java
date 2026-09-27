package com.nxtime.nxtime.security;

import com.nxtime.nxtime.domain.RefreshToken;
import com.nxtime.nxtime.exception.BusinessException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * La sesión del navegador: el refresh token en una cookie que JavaScript no
 * puede leer, y la protección CSRF que esa cookie obliga a poner (ADR 030).
 *
 * <h2>Por qué hasta ahora no había cookie</h2>
 *
 * Con la web en {@code nxtime-web.onrender.com} y la API en
 * {@code nxtime-backend.onrender.com} no se podía: {@code onrender.com} está en
 * la Public Suffix List, así que eran dos sitios distintos y una cookie
 * {@code SameSite} no viajaba (ADR 020). Con el dominio propio, la web
 * ({@code nxtime-web.com}) y la API ({@code api.nxtime-web.com}) son el mismo
 * sitio, y la cookie viaja en un {@code fetch} con {@code credentials: 'include'}.
 *
 * <h2>Las dos cookies</h2>
 *
 * <ul>
 *   <li>{@value #COOKIE_REFRESH}: el refresh. {@code HttpOnly} (un XSS no se
 *       lo lleva), {@code Secure}, {@code SameSite=Strict} y solo en
 *       {@code /auth}: no viaja con cada petición de la API, solo con las dos
 *       que la necesitan. Sin {@code Domain}, así que es solo de la API.</li>
 *   <li>{@value #COOKIE_CSRF}: un valor aleatorio que la web SÍ puede leer, en
 *       el dominio común ({@code application.security.web-session.cookie-domain})
 *       para que la página, que está en otro subdominio, lo vea.</li>
 * </ul>
 *
 * <h2>El CSRF, que es el error clásico al poner la cookie</h2>
 *
 * Un token en la cabecera {@code Authorization} no lo puede poner un
 * formulario de otra web; una cookie la manda el navegador sola. Así que las
 * peticiones que se autentican con {@value #COOKIE_REFRESH} exigen además:
 *
 * <ol>
 *   <li><b>Doble envío</b>: la cabecera {@value #CABECERA_CSRF} con el mismo
 *       valor que la cookie {@value #COOKIE_CSRF}. Otra web no puede leer esa
 *       cookie, así que no puede copiarla en la cabecera.</li>
 *   <li><b>Un {@code Origin} de la lista blanca</b>, si viene. Los navegadores
 *       lo mandan siempre en un POST entre orígenes; es la segunda llave, por
 *       si algún día la primera fallara.</li>
 * </ol>
 *
 * {@code SameSite=Strict} ya lo pararía en la práctica, pero apoyar toda la
 * defensa en un atributo que algún navegador podría relajar es justo el
 * razonamiento que envejece mal (ADR 020).
 *
 * <b>La app Android no pasa por aquí</b>: sigue mandando el refresh en el
 * cuerpo, no tiene cookies y no le afecta nada de esto.
 */
@Component
public class SesionWeb {

    public static final String COOKIE_REFRESH = "nx_refresh";
    public static final String COOKIE_CSRF = "nx_csrf";
    public static final String CABECERA_CSRF = "X-CSRF-Token";

    /** Las dos rutas que usan la cookie del refresh, y ninguna más. */
    private static final String RUTA_REFRESH = "/auth";

    private final String dominioComun;
    private final List<String> origenesPermitidos;
    private final SecureRandom azar = new SecureRandom();

    public SesionWeb(
            @Value("${application.security.web-session.cookie-domain:}") String dominioComun,
            @Value("${application.security.cors.allowed-origins}") String origenesRaw
    ) {
        this.dominioComun = dominioComun.trim();
        this.origenesPermitidos = Arrays.stream(origenesRaw.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isBlank())
                .toList();
    }

    /** Tras un login o un refresco desde la web: el refresh nuevo y un CSRF nuevo. */
    public void emitir(HttpServletResponse respuesta, String refreshToken) {
        Duration vida = RefreshToken.Origen.WEB.duracion();
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookieRefresh(refreshToken, vida).toString());
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookieCsrf(valorAleatorio(), vida).toString());
    }

    /** Al cerrar sesión: las dos cookies, caducadas. */
    public void borrar(HttpServletResponse respuesta) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookieRefresh("", Duration.ZERO).toString());
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookieCsrf("", Duration.ZERO).toString());
    }

    public Optional<String> refreshDeLaCookie(HttpServletRequest peticion) {
        return valorDe(peticion, COOKIE_REFRESH);
    }

    /**
     * Para toda petición que se autentica con la cookie del refresh.
     *
     * Responde 403 con un mensaje que no dice cuál de las comprobaciones ha
     * fallado: a quien está probando no hay por qué contárselo.
     */
    public void comprobarCsrf(HttpServletRequest peticion) {
        String cabecera = peticion.getHeader(CABECERA_CSRF);
        Optional<String> cookie = valorDe(peticion, COOKIE_CSRF);
        boolean coinciden = cabecera != null && cookie.isPresent()
                && MessageDigest.isEqual(
                        cabecera.getBytes(StandardCharsets.UTF_8),
                        cookie.get().getBytes(StandardCharsets.UTF_8));

        String origen = peticion.getHeader(HttpHeaders.ORIGIN);
        boolean origenValido = origen == null
                || origenesPermitidos.contains("*")
                || origenesPermitidos.contains(origen);

        if (!coinciden || !origenValido) {
            throw new BusinessException("La petición no viene de la aplicación. Vuelve a entrar.", HttpStatus.FORBIDDEN);
        }
    }

    private ResponseCookie cookieRefresh(String valor, Duration vida) {
        return ResponseCookie.from(COOKIE_REFRESH, valor)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(RUTA_REFRESH)
                .maxAge(vida)
                .build();
    }

    private ResponseCookie cookieCsrf(String valor, Duration vida) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(COOKIE_CSRF, valor)
                // Legible a propósito: la web la copia en la cabecera. No da
                // acceso a nada por sí sola; sin la del refresh no vale.
                .httpOnly(false)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(vida);
        // Sin dominio común (en local), la cookie es del host, y como la web
        // habla con la API a través del proxy de Vite, es el mismo host.
        if (!dominioComun.isEmpty()) {
            cookie.domain(dominioComun);
        }
        return cookie.build();
    }

    private String valorAleatorio() {
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static Optional<String> valorDe(HttpServletRequest peticion, String nombre) {
        Cookie[] cookies = peticion.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> nombre.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst();
    }
}
