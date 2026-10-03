package com.nxtime.nxtime.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * El valor de {@code Content-Disposition} para un fichero cuyo nombre eligió
 * quien lo subió (revisión de seguridad del 1/10/2026).
 *
 * <p>Antes se pegaba el nombre entre comillas tal cual: un CV llamado
 * {@code cv".pdf} cerraba las comillas antes de tiempo y lo que venía detrás
 * se leía como otro parámetro de la cabecera. Aquí sale dos veces, como pide
 * la RFC 6266:
 * <ul>
 *   <li>{@code filename="..."}: solo ASCII imprimible sin comillas ni barras;
 *       lo demás pasa a {@code _}. Es el respaldo para clientes viejos.</li>
 *   <li>{@code filename*=UTF-8''...}: el nombre entero, codificado (RFC 5987).
 *       Es el que leen los navegadores y la web ({@code util/descargar.ts}),
 *       así que «currículum.pdf» se descarga con su tilde.</li>
 * </ul>
 */
public final class CabeceraDeDescarga {

    private CabeceraDeDescarga() {
    }

    /**
     * @param disposicion {@code attachment} o {@code inline}.
     * @param nombre el nombre del fichero, tal como se guardó.
     */
    public static String de(String disposicion, String nombre) {
        return disposicion
                + "; filename=\"" + soloAscii(nombre) + "\""
                + "; filename*=UTF-8''" + codificado(nombre);
    }

    private static String soloAscii(String nombre) {
        StringBuilder limpio = new StringBuilder(nombre.length());
        for (char c : nombre.toCharArray()) {
            boolean seguro = c >= 0x20 && c < 0x7f && c != '"' && c != '\\' && c != '/' && c != ';';
            limpio.append(seguro ? c : '_');
        }
        return limpio.toString();
    }

    /** RFC 5987: todo lo que no sea attr-char, en %XX. URLEncoder deja pasar «*» y pone «+» por espacio. */
    private static String codificado(String nombre) {
        return URLEncoder.encode(nombre, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A");
    }
}
