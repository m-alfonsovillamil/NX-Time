package com.nxtime.nxtime.domain;

import java.util.regex.Pattern;

/**
 * Las reglas del código de empleado (V49, ADR 053), en un solo sitio para la
 * ficha y para la importación de plantilla.
 *
 * <ul>
 *   <li>hasta 50 caracteres;</li>
 *   <li>letras, números y {@code . _ / -}, sin espacios: es lo que usan los
 *       números de personal de los ERP y de los programas de nóminas
 *       («000123», «EMP-0042», «MAD/17»);</li>
 *   <li>empieza por letra o número. Además de ser lo normal, deja fuera
 *       {@code = + - @}, con los que una hoja de cálculo tomaría la celda por
 *       una fórmula al abrir una exportación;</li>
 *   <li>único dentro de la empresa sin distinguir mayúsculas (lo garantiza el
 *       índice de V49; aquí solo se comprueba antes para dar un mensaje
 *       claro).</li>
 * </ul>
 */
public final class CodigosDeEmpleado {

    public static final int LONGITUD_MAXIMA = 50;

    private static final Pattern FORMATO = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N}._/-]*$");

    private CodigosDeEmpleado() {
    }

    /** Sin espacios alrededor; null si no queda nada. */
    public static String normalizar(String codigo) {
        if (codigo == null) {
            return null;
        }
        String limpio = codigo.strip();
        return limpio.isEmpty() ? null : limpio;
    }

    /** Lo que tiene de malo un código ya normalizado, o null si vale (y null vale: es «sin código»). */
    public static String problema(String codigo) {
        if (codigo == null) {
            return null;
        }
        if (codigo.length() > LONGITUD_MAXIMA) {
            return "El código de empleado no puede pasar de " + LONGITUD_MAXIMA + " caracteres.";
        }
        if (!FORMATO.matcher(codigo).matches()) {
            return "El código de empleado solo puede llevar letras, números y . _ / -, sin espacios, "
                    + "y tiene que empezar por letra o número.";
        }
        return null;
    }

    /** Para comparar dos códigos como los compara el índice único. */
    public static String clave(String codigo) {
        return codigo == null ? null : codigo.toUpperCase(java.util.Locale.ROOT);
    }
}
