package com.nxtime.nxtime.security.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtime.nxtime.domain.SsoProvider;
import io.jsonwebtoken.io.Decoders;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Lo que hay que recordar entre mandar a alguien al proveedor y verlo volver.
 *
 * El servidor no guarda sesiones (la cadena es STATELESS), así que viaja en una
 * cookie, <b>firmada</b>: quien la trae de vuelta no puede haberla escrito ni
 * retocado. Lleva el {@code state} (que la vuelta sea de ESTA ida), el
 * {@code nonce} (que el ID token sea de esta ida) y el verificador de PKCE (que
 * el código lo canjee quien lo pidió).
 *
 * <h2>{@code SameSite=Lax}, y no {@code Strict} como las de la sesión</h2>
 *
 * La vuelta es una navegación que empieza en {@code accounts.google.com}: con
 * {@code Strict} el navegador no mandaría esta cookie y no habría con qué
 * comparar el {@code state}. {@code Lax} la manda en navegaciones de primer
 * nivel por GET, que es justo esto, y no en peticiones incrustadas desde otra
 * web. Por eso también se pide a Microsoft que vuelva por la URL y no con un
 * formulario ({@code form_post}): un POST entre sitios no la traería.
 *
 * <h2>La firma</h2>
 *
 * HMAC-SHA256 con una clave <b>derivada</b> de la del JWT, no con la misma: una
 * cookie de estado no se puede hacer pasar por un token de acceso ni al revés,
 * aunque algún día compartieran formato.
 */
@Component
public class SsoState {

    public static final String COOKIE = "nx_sso";

    /** Diez minutos para ir al proveedor, elegir cuenta y volver. */
    public static final Duration VIDA = Duration.ofMinutes(10);

    /** Solo viaja a las rutas del SSO. */
    private static final String RUTA = "/auth/sso";

    private static final String CONTEXTO_DE_LA_CLAVE = "nx-time/sso/estado/v1";

    /** Para qué es esta ida al proveedor. */
    public enum Modo {
        /** Entrar desde la web: al volver se ponen las cookies de la sesión. */
        WEB,
        /** Entrar desde la app: al volver se le da un código que canjea ella. */
        APP,
        /** Añadir esta cuenta a la sesión de NX Time que ya hay abierta. */
        VINCULAR
    }

    /**
     * @param retoDeLaApp el {@code code_challenge} que mandó la app (modo APP), o null
     * @param usuarioId   quién vincula (modo VINCULAR), o null
     * @param expira      segundos desde la época
     */
    public record Estado(
            SsoProvider proveedor,
            Modo modo,
            String state,
            String nonce,
            String verificador,
            String retoDeLaApp,
            Long usuarioId,
            long expira) {
    }

    private final ObjectMapper json;
    private final byte[] clave;

    public SsoState(ObjectMapper json, @Value("${application.security.jwt.secret-key}") String claveDelJwt) {
        this.json = json;
        this.clave = hmac(Decoders.BASE64.decode(claveDelJwt), CONTEXTO_DE_LA_CLAVE.getBytes(StandardCharsets.UTF_8));
    }

    public void guardar(HttpServletResponse respuesta, Estado estado) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie(sellar(estado), VIDA).toString());
    }

    /** Se usa una vez: en cuanto se lee a la vuelta, se borra. */
    public void borrar(HttpServletResponse respuesta) {
        respuesta.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    /** El estado de la cookie, si la hay, la firma es buena y no ha caducado. */
    public Optional<Estado> leer(HttpServletRequest peticion) {
        Cookie[] cookies = peticion.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .flatMap(this::abrir);
    }

    String sellar(Estado estado) {
        try {
            String cuerpo = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(estado));
            return cuerpo + "." + firmaDe(cuerpo);
        } catch (Exception e) {
            throw new IllegalStateException("No se ha podido sellar el estado del SSO", e);
        }
    }

    Optional<Estado> abrir(String sellado) {
        if (sellado == null) {
            return Optional.empty();
        }
        int punto = sellado.lastIndexOf('.');
        if (punto <= 0) {
            return Optional.empty();
        }
        String cuerpo = sellado.substring(0, punto);
        String firma = sellado.substring(punto + 1);
        // La firma ANTES de mirar nada de dentro, y sin atajos: comparar con
        // equals() tarda menos cuanto antes difieren, y eso se puede medir.
        if (!MessageDigest.isEqual(
                firmaDe(cuerpo).getBytes(StandardCharsets.UTF_8), firma.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        try {
            Estado estado = json.readValue(Base64.getUrlDecoder().decode(cuerpo), Estado.class);
            return estado.expira() > Instant.now().getEpochSecond() ? Optional.of(estado) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String firmaDe(String cuerpo) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(clave, cuerpo.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmac(byte[] clave, byte[] datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return mac.doFinal(datos);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);
        }
    }

    private static ResponseCookie cookie(String valor, Duration vida) {
        return ResponseCookie.from(COOKIE, valor)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path(RUTA)
                .maxAge(vida)
                .build();
    }
}
