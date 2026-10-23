package com.nxtime.nxtime.kiosco;

import com.nxtime.nxtime.web.QrEnSvg;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La tarjeta QR con la que una persona ficha en un kiosco (ADR 033).
 *
 * <b>No hay ningún secreto guardado por persona.</b> El contenido del QR es
 * {@code NXK1.<usuarioId>.<versión>.<firma>}, y la firma es un HMAC-SHA256 del
 * servidor sobre el id y la versión. Para comprobar una tarjeta basta
 * recalcular la firma y mirar que la versión sea la vigente
 * ({@code usuarios.kiosco_tarjeta_version}); regenerarla sube la versión y deja
 * sin valor todas las anteriores, impresas o no.
 *
 * La clave se deriva del secreto de los JWT con una etiqueta propia, para no
 * usar la misma clave para dos cosas. Consecuencia, dicha en el ADR: rotar
 * {@code JWT_SECRET} invalida todas las tarjetas, igual que cierra todas las
 * sesiones.
 *
 * La tarjeta solo sirve para fichar en un kiosco de su empresa: no abre ninguna
 * sesión ni enseña nada. Por eso RRHH puede imprimir las de la plantilla.
 */
@Component
public class TarjetaDeKiosco {

    static final String PREFIJO = "NXK1";

    /** 16 caracteres hexadecimales: 64 bits de firma. */
    private static final int LARGO_FIRMA = 16;

    private final SecretKeySpec clave;

    public TarjetaDeKiosco(@Value("${application.security.jwt.secret-key}") String secretoJwt) {
        this.clave = new SecretKeySpec(derivar(secretoJwt), "HmacSHA256");
    }

    /** Lo que va dentro del QR. */
    public String codigo(long usuarioId, int version) {
        return PREFIJO + "." + usuarioId + "." + version + "." + firma(usuarioId, version);
    }

    /** De quién es y qué versión dice ser, si la firma es buena. No mira si la versión sigue vigente. */
    public Optional<Lectura> leer(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        String[] partes = codigo.trim().split("\\.");
        if (partes.length != 4 || !PREFIJO.equals(partes[0])) {
            return Optional.empty();
        }
        try {
            long usuarioId = Long.parseLong(partes[1]);
            int version = Integer.parseInt(partes[2]);
            // Comparación en tiempo constante: no se puede ir adivinando la
            // firma carácter a carácter por lo que tarda en decir que no.
            boolean firmaBuena = MessageDigest.isEqual(
                    firma(usuarioId, version).getBytes(StandardCharsets.US_ASCII),
                    partes[3].getBytes(StandardCharsets.US_ASCII));
            return firmaBuena ? Optional.of(new Lectura(usuarioId, version)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** El QR como SVG: ver {@link QrEnSvg}. */
    public String svg(String codigo) {
        return QrEnSvg.de(codigo, "Tarjeta para fichar en el kiosco");
    }

    private String firma(long usuarioId, int version) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(clave);
            byte[] resumen = mac.doFinal((usuarioId + ":" + version).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen).substring(0, LARGO_FIRMA);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);
        }
    }

    private static byte[] derivar(String secretoJwt) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(("nxtime-tarjeta-kiosco:" + secretoJwt).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public record Lectura(long usuarioId, int version) {
    }
}
