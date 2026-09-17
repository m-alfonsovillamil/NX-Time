package com.nxtime.nxtime.domain;

import java.util.Locale;

/**
 * Normaliza direcciones de correo a una forma única.
 *
 * Por qué existe (16/09/2026): el correo se buscaba con una comparación
 * exacta, así que quien escribía su dirección con una mayúscula se quedaba
 * fuera. Y en silencio: el login devolvía "credenciales incorrectas" y
 * /auth/recuperar devuelve 202 pase lo que pase (ADR 014, para no revelar
 * qué direcciones tienen cuenta), de modo que ni la persona ni nosotros
 * teníamos forma de enterarnos. Costó cuatro días descubrirlo con el correo
 * de producción, que fallaba por lo mismo en el remitente.
 *
 * La parte de dominio de una dirección no distingue mayúsculas (RFC 1035) y
 * la parte local, en teoría, sí -- pero ningún proveedor real trata a
 * "Juan@x.com" y "juan@x.com" como dos buzones, y desde luego nadie espera
 * que una aplicación lo haga. Se normaliza entera.
 *
 * Locale.ROOT y no el del sistema: con la configuración regional turca,
 * "I".toLowerCase() da "ı" (i sin punto) y una dirección con I mayúscula
 * dejaría de encontrarse. Es el clásico "Turkish I problem".
 */
public final class Emails {

    private Emails() {
    }

    /** Recorta espacios y pasa a minúsculas. Tolera null para no estorbar a @NotBlank. */
    public static String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
