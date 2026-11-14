package com.nxtime.nxtime.config;

import java.awt.Color;

/**
 * Los colores de la marca para lo que el backend pinta él mismo: correos, PDF
 * y Excel. Es la línea visual «Fichaje» de la app y la web, con la paleta
 * arena (ADR 057): fondo arena, azul noche para las acciones y tarjetas marfil.
 *
 * <b>No es la fuente:</b> la fuente es el tema de Android
 * (`ui/theme/Color.kt`), del que la web genera `tokens.css`. Aquí van copiados
 * solo los que hacen falta, con el nombre que tienen allí, y `MarcaTest`
 * comprueba que siguen siendo iguales: un correo de un color y una app de otro
 * es lo que había hasta noviembre de 2026.
 *
 * Solo la paleta clara: los clientes de correo y los PDF no tienen modo oscuro
 * fiable, y un documento impreso siempre es claro.
 */
public final class Marca {

    /** El primario (`LightPrimary`): botones, cabeceras, enlaces. Blanco encima: 11,5:1. */
    public static final String PRIMARIO = "#1F3A5F";
    /** Texto sobre el primario (`LightOnPrimary`). */
    public static final String SOBRE_PRIMARIO = "#FFFFFF";
    /** Contenedor del primario (`LightPrimaryContainer`): filas resaltadas, totales. */
    public static final String CONTENEDOR_PRIMARIO = "#D6E0EF";
    /** Texto sobre ese contenedor (`LightOnPrimaryContainer`). */
    public static final String SOBRE_CONTENEDOR_PRIMARIO = "#0B1E38";
    /** Texto normal (`LightOnSurface`). */
    public static final String TEXTO = "#1C2327";
    /** Texto secundario (`LightOnSurfaceVariant`): pies, notas. */
    public static final String TEXTO_SECUNDARIO = "#565040";
    /** Bordes suaves (`LightOutlineVariant`). */
    public static final String BORDE = "#D6CBB5";
    /** El fondo, donde empieza el degradado (`LightFondoArriba`). */
    public static final String FONDO = "#F6EFE1";
    /** La tarjeta de un correo (`LightSurface`): marfil, no blanco. */
    public static final String TARJETA = "#FFFAF1";
    /** Errores y avisos (`LightError`). */
    public static final String ERROR = "#BA1A1A";
    /**
     * Lo que ha ido bien: una ausencia aprobada. Es el verde de «trabajando»
     * (`ColoresJornadaClaro.trabajando`), el que la app ya enseña. Blanco
     * encima: 6,6:1; sobre blanco, igual.
     */
    public static final String EXITO = "#2E6B34";

    private Marca() {
    }

    /** Para OpenPDF, que pinta con {@link java.awt.Color}. */
    public static Color awt(String hex) {
        return Color.decode(hex);
    }

    /** Para POI, que quiere los tres bytes RGB. */
    public static byte[] rgb(String hex) {
        Color color = awt(hex);
        return new byte[] {(byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue()};
    }
}
