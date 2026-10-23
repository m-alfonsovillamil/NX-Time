package com.nxtime.nxtime.security.totp;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Los códigos de 6 cifras de las aplicaciones de autenticación (RFC 6238).
 *
 * Con los parámetros que entienden todas (Google Authenticator, Microsoft
 * Authenticator, 1Password, Bitwarden...): SHA-1, 6 dígitos y 30 segundos.
 * Otros valores están en la norma, pero hay aplicaciones que los ignoran sin
 * avisar y generan códigos que no valen.
 *
 * Sin dependencias: es un HMAC y una cuenta, y una librería más es una pieza
 * más que vigilar. Los vectores de prueba del apéndice B de la RFC están en
 * {@code TotpTest}.
 */
public final class Totp {

    /** Segundos que dura cada código. */
    public static final int PERIODO = 30;

    public static final int DIGITOS = 6;

    /**
     * Pasos de margen a cada lado del actual: el código anterior y el
     * siguiente también valen. Cubre un reloj de móvil desviado unos segundos
     * y a quien escribe el código justo cuando cambia (RFC 6238, apartado 5.2).
     */
    public static final int VENTANA = 1;

    /** 160 bits, lo que recomienda la RFC 4226 para HMAC-SHA1. */
    static final int BYTES_DEL_SECRETO = 20;

    private static final SecureRandom AZAR = new SecureRandom();
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Totp() {
    }

    /** Un secreto nuevo. */
    public static byte[] nuevoSecreto() {
        byte[] secreto = new byte[BYTES_DEL_SECRETO];
        AZAR.nextBytes(secreto);
        return secreto;
    }

    /** El paso (el número de intervalo de 30 s) de un instante. */
    public static long pasoDe(Instant instante) {
        return Math.floorDiv(instante.getEpochSecond(), PERIODO);
    }

    /** El código de un paso, con sus ceros a la izquierda. */
    public static String codigo(byte[] secreto, long paso) {
        byte[] hmac;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secreto, "HmacSHA1"));
            hmac = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
        } catch (GeneralSecurityException e) {
            // HmacSHA1 está garantizado en cualquier JVM estándar.
            throw new IllegalStateException("HmacSHA1 no disponible en esta JVM", e);
        }
        // Truncado dinámico (RFC 4226, apartado 5.3).
        int desde = hmac[hmac.length - 1] & 0x0f;
        int binario = ((hmac[desde] & 0x7f) << 24)
                | ((hmac[desde + 1] & 0xff) << 16)
                | ((hmac[desde + 2] & 0xff) << 8)
                | (hmac[desde + 3] & 0xff);
        int codigo = binario % 1_000_000;
        return String.format("%0" + DIGITOS + "d", codigo);
    }

    /**
     * El paso cuyo código es el que se ha escrito, dentro de la ventana y
     * posterior a {@code ultimoPasoUsado}; vacío si no hay ninguno.
     *
     * Lo de «posterior» es lo que impide reutilizar un código: quien lo haya
     * visto por encima del hombro, o lo pesque en un registro, llega tarde.
     * Quien llama tiene que guardar el paso devuelto como el nuevo último.
     *
     * Se recorren todos los pasos de la ventana aunque uno ya coincida, y se
     * comparan con {@link MessageDigest#isEqual}: el tiempo de respuesta no
     * dice cuál de ellos era.
     */
    public static OptionalLong verificar(byte[] secreto, String escrito, Instant ahora, Long ultimoPasoUsado) {
        if (escrito == null || escrito.length() != DIGITOS || !escrito.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        byte[] esperado = escrito.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        long actual = pasoDe(ahora);
        long encontrado = Long.MIN_VALUE;
        for (long paso = actual - VENTANA; paso <= actual + VENTANA; paso++) {
            boolean coincide = MessageDigest.isEqual(esperado,
                    codigo(secreto, paso).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            boolean nuevo = ultimoPasoUsado == null || paso > ultimoPasoUsado;
            if (coincide && nuevo && encontrado == Long.MIN_VALUE) {
                encontrado = paso;
            }
        }
        return encontrado == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(encontrado);
    }

    /** El secreto en Base32 sin relleno, que es como lo escriben las aplicaciones. */
    public static String base32(byte[] datos) {
        StringBuilder salida = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                salida.append(BASE32[(buffer >> (bits - 5)) & 0x1f]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            salida.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
        }
        return salida.toString();
    }

    /**
     * La dirección {@code otpauth://} que lleva el QR (el formato de Google
     * Authenticator, que entienden todas). La etiqueta dice el servicio y la
     * cuenta, para que en la aplicación se distinga de las demás.
     */
    public static String uri(String emisor, String cuenta, byte[] secreto) {
        String etiqueta = codificar(emisor) + ":" + codificar(cuenta);
        return "otpauth://totp/" + etiqueta
                + "?secret=" + base32(secreto)
                + "&issuer=" + codificar(emisor)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PERIODO;
    }

    // URLEncoder pone «+» en los espacios, y en una ruta tienen que ser %20.
    private static String codificar(String texto) {
        return java.net.URLEncoder.encode(texto, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
