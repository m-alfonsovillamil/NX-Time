package com.nxtime.nxtime.security.sso;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Los códigos con los que la app Android recoge su sesión tras un SSO.
 *
 * La app abre el navegador, la persona entra en el proveedor y el servidor
 * tiene que devolverle la sesión a la app. No puede ponerla en la URL de
 * vuelta ({@code nxtime://sso?...}): esa URL la ve el sistema, puede acabar en
 * un registro, y cualquier otra app puede declarar que atiende ese mismo
 * esquema. Así que por la URL viaja solo un <b>código</b>, y la app lo canjea
 * por la sesión en una petición suya.
 *
 * Para que el código no le sirva a quien lo intercepte:
 * <ul>
 *   <li>vale <b>una vez</b> y dura <b>un minuto</b>;</li>
 *   <li>va <b>atado a un secreto de la app</b>: al empezar, la app manda el
 *       resumen SHA-256 de un valor aleatorio que se queda ella, y al canjear
 *       tiene que presentar el valor. Es PKCE, aplicado a este tramo. Otra app
 *       que se quede con el código no tiene con qué canjearlo.</li>
 * </ul>
 *
 * <b>En memoria, a propósito.</b> Viven un minuto y hay una sola instancia del
 * backend: si se reinicia justo entonces, la persona vuelve a pulsar el botón.
 * Guardarlos en la base sería una tabla más y una escritura más para eso.
 */
@Component
public class AppExchangeCodes {

    static final Duration VIDA = Duration.ofSeconds(60);

    /** Tope de códigos vivos. Cada uno exige un SSO completo, así que es de sobra. */
    private static final int MAXIMO = 10_000;

    /**
     * Lo que espera a que la app lo recoja: la persona a la que abrirle la
     * sesión, o la cuenta del proveedor que quiere vincular. Nunca las dos.
     */
    private record Pendiente(Long usuarioId, VerifiedIdentity paraVincular, String reto, Instant caduca) {
    }

    private final Map<String, Pendiente> pendientes = new ConcurrentHashMap<>();
    private final SecureRandom azar = new SecureRandom();
    private final Clock reloj;

    public AppExchangeCodes() {
        this(Clock.systemUTC());
    }

    AppExchangeCodes(Clock reloj) {
        this.reloj = reloj;
    }

    /** Un código para esta persona, atado al reto que mandó la app al empezar. */
    public String emitir(long usuarioId, String reto) {
        return guardar(usuarioId, null, reto);
    }

    /**
     * Un código para <b>vincular</b> esa cuenta del proveedor. No dice a quién:
     * eso lo dirá la sesión de quien lo confirme. Así el tramo del navegador no
     * lleva nada que ate la cuenta a una persona, y un enlace que alguien le
     * mande a otro para que «vincule» no consigue nada: el código vuelve a la
     * app de la víctima, que no tiene el verificador de esa ida.
     */
    public String emitirParaVincular(VerifiedIdentity identidad, String reto) {
        return guardar(null, identidad, reto);
    }

    private String guardar(Long usuarioId, VerifiedIdentity paraVincular, String reto) {
        Instant ahora = reloj.instant();
        pendientes.values().removeIf(pendiente -> !pendiente.caduca().isAfter(ahora));
        if (pendientes.size() >= MAXIMO) {
            throw new SsoException(SsoException.Motivo.FALLO, "Demasiados códigos de canje pendientes");
        }
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // Se guarda su resumen, no el código: un volcado de memoria no lo regala.
        pendientes.put(sha256Hex(codigo), new Pendiente(usuarioId, paraVincular, reto, ahora.plus(VIDA)));
        return codigo;
    }

    /**
     * De quién es el código, si es bueno. <b>Se gasta al presentarlo</b>, acierte
     * o no el verificador: quien lo intercepta no tiene varios intentos.
     */
    public Optional<Long> canjear(String codigo, String verificador) {
        return recoger(codigo, verificador).map(Pendiente::usuarioId);
    }

    /**
     * La cuenta del proveedor que ese código permite vincular. Se gasta igual.
     * Un código de sesión no vale aquí, ni uno de vincular en {@link #canjear}:
     * presentarlo en el sitio que no es lo quema y no da nada.
     */
    public Optional<VerifiedIdentity> canjearParaVincular(String codigo, String verificador) {
        return recoger(codigo, verificador).map(Pendiente::paraVincular);
    }

    private Optional<Pendiente> recoger(String codigo, String verificador) {
        if (codigo == null || verificador == null) {
            return Optional.empty();
        }
        Pendiente pendiente = pendientes.remove(sha256Hex(codigo));
        if (pendiente == null || !pendiente.caduca().isAfter(reloj.instant())) {
            return Optional.empty();
        }
        boolean esSuyo = MessageDigest.isEqual(
                retoDe(verificador).getBytes(StandardCharsets.UTF_8),
                pendiente.reto().getBytes(StandardCharsets.UTF_8));
        return esSuyo ? Optional.of(pendiente) : Optional.empty();
    }

    /** El reto que corresponde a un verificador: su SHA-256 en base64url, como en PKCE (S256). */
    public static String retoDe(String verificador) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verificador));
    }

    private static String sha256Hex(String texto) {
        return HexFormat.of().formatHex(sha256(texto));
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
