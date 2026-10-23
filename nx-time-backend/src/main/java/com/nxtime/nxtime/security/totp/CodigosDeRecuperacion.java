package com.nxtime.nxtime.security.totp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Los códigos de recuperación: para entrar el día que se pierde el móvil.
 *
 * Diez, de un solo uso, con la forma {@code abcde-23456}: diez caracteres de
 * un alfabeto sin los que se confunden al copiarlos a mano (ni {@code 0/o}, ni
 * {@code 1/l/i}). Son unos 49 bits de azar cada uno, que con el límite de
 * intentos por cuenta no se adivinan probando.
 *
 * En la base solo va su SHA-256: con ese azar, un hash lento no añade nada, y
 * así se pueden buscar por igualdad.
 */
public final class CodigosDeRecuperacion {

    public static final int CUANTOS = 10;

    private static final char[] ALFABETO = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();
    private static final int LETRAS = 10;
    private static final SecureRandom AZAR = new SecureRandom();

    private CodigosDeRecuperacion() {
    }

    /** Diez códigos nuevos, en claro: se enseñan una vez y no se guardan así. */
    public static List<String> nuevos() {
        List<String> codigos = new ArrayList<>(CUANTOS);
        for (int i = 0; i < CUANTOS; i++) {
            StringBuilder codigo = new StringBuilder(LETRAS + 1);
            for (int j = 0; j < LETRAS; j++) {
                if (j == LETRAS / 2) {
                    codigo.append('-');
                }
                codigo.append(ALFABETO[AZAR.nextInt(ALFABETO.length)]);
            }
            codigos.add(codigo.toString());
        }
        return codigos;
    }

    /**
     * Si lo escrito tiene pinta de código de recuperación y no de código de la
     * aplicación: así un solo campo sirve para los dos.
     */
    public static boolean parece(String escrito) {
        return escrito != null && normalizar(escrito).length() == LETRAS;
    }

    /** Lo escrito sin guion, espacios ni mayúsculas: como se copie, vale. */
    static String normalizar(String escrito) {
        return escrito.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
    }

    public static String hash(String escrito) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(normalizar(escrito).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
