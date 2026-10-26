package com.nxtime.nxtime.domain;

/**
 * Desde dónde se fichó (V50, ADR 054). Es el valor de {@code registros.origen}
 * y del CHECK de la base: constantes en español, como el resto de enums.
 *
 * Lo anterior a V50 lo tiene a null (no se sabe), salvo lo del kiosco.
 */
public enum OrigenDelFichaje {
    /** Desde la web, con la sesión de la persona. */
    WEB,
    /** Desde la app Android, con conexión. */
    APP,
    /** En un kiosco (ADR 033). */
    KIOSCO,
    /**
     * Desde la app, sin conexión: se guardó en el móvil y llegó después. La
     * hora es la del fichaje, no la de llegada; las dos constan en el motivo
     * de la auditoría.
     */
    APP_SIN_CONEXION;

    /**
     * El cliente que dice la cabecera {@code X-Cliente}, o null si no la manda
     * o no se reconoce (una app o una web anteriores a V50).
     */
    public static OrigenDelFichaje deCabecera(String cliente) {
        if (cliente == null) {
            return null;
        }
        return switch (cliente.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "web" -> WEB;
            case "android" -> APP;
            default -> null;
        };
    }
}
