package com.nxtime.nxtime.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * El código de seguimiento de una denuncia anónima (Fase G): se genera
 * una vez, se entrega una vez y de él solo se guarda el hash.
 *
 * <b>Por qué SHA-256 sin sal y no BCrypt.</b> Esto es una credencial,
 * como una contraseña, pero no se parece a una contraseña en lo único
 * que importa aquí:
 *
 * <ul>
 *   <li>Se busca POR ella. Llega un código y hay que encontrar su fila,
 *       así que el hash tiene que ser determinista e indexable. Con
 *       BCrypt — sal distinta por fila — habría que recorrer la tabla
 *       entera comparando una a una, y BCrypt es lento a propósito.</li>
 *   <li>No la elige una persona. Es un UUIDv4: 122 bits de azar, sin
 *       diccionario que probar ni patrón que adivinar. Lo que la sal y
 *       el coste de BCrypt protegen — contraseñas humanas, reutilizadas
 *       y de poca entropía — aquí no existe.</li>
 * </ul>
 *
 * Lo que sí comparte con una contraseña es lo esencial: el valor en
 * claro no se guarda en ningún sitio. Ni en la base, ni en un log, ni
 * en un correo. Ver ADR 012.
 */
public final class TrackingCode {

    private TrackingCode() {
    }

    /**
     * Un código nuevo. {@code UUID.randomUUID()} usa
     * {@code SecureRandom}, que es lo que hace que no se pueda predecir
     * el código de otra persona a partir del propio.
     */
    public static String generar() {
        return UUID.randomUUID().toString();
    }

    /** El hash que se guarda: SHA-256 en hexadecimal, 64 caracteres. */
    public static String hash(String codigo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codigo.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM desde siempre. Si
            // faltara, seguir sin él significaría guardar el código en
            // claro, y eso no es un modo degradado aceptable para esto.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
