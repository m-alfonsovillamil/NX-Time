package com.nxtime.nxtime.kiosco;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
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

    /**
     * El QR como SVG, listo para pintar en la web, en la app o en papel.
     *
     * Se escribe a mano a partir de la matriz de ZXing, un rectángulo por
     * módulo oscuro en un solo {@code path}: así ningún cliente necesita una
     * librería de QR, y el SVG escala sin perder nitidez al imprimirlo.
     */
    public String svg(String codigo) {
        try {
            BitMatrix matriz = new QRCodeWriter().encode(codigo, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 4));
            int ancho = matriz.getWidth();
            int alto = matriz.getHeight();
            StringBuilder trazo = new StringBuilder();
            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    if (matriz.get(x, y)) {
                        trazo.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                    }
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + ancho + " " + alto
                    + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"Tarjeta para fichar en el kiosco\">"
                    + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                    + "<path fill=\"#000\" d=\"" + trazo + "\"/></svg>";
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el QR", e);
        }
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
