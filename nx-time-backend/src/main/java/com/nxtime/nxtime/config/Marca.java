package com.nxtime.nxtime.config;

import java.awt.Color;

/**
 * Los colores de la marca para lo que el backend pinta él mismo: correos, PDF
 * y Excel. Es la línea visual «Fichaje» de la app y la web.
 *
 * <b>No es la fuente:</b> la fuente es el tema de Android
 * (`ui/theme/Color.kt`), del que la web genera `tokens.css`. Aquí van copiados
 * solo los que hacen falta, con el nombre que tienen allí, y `MarcaTest`
 * comprueba que siguen siendo iguales: un correo azul con una app teal es lo
 * que había hasta noviembre de 2026.
 *
 * Solo la paleta clara: los clientes de correo y los PDF no tienen modo oscuro
 * fiable, y un documento impreso siempre es claro.
 */
public final class Marca {

    /** El primario (`LightPrimary`): botones, cabeceras, enlaces. Blanco encima: 5,0:1. */
    public static final String PRIMARIO = "#0E7C86";
    /** Texto sobre el primario (`LightOnPrimary`). */
    public static final String SOBRE_PRIMARIO = "#FFFFFF";
    /** Contenedor del primario (`LightPrimaryContainer`): filas resaltadas, totales. */
    public static final String CONTENEDOR_PRIMARIO = "#CDEBEE";
    /** Texto sobre ese contenedor (`LightOnPrimaryContainer`). */
    public static final String SOBRE_CONTENEDOR_PRIMARIO = "#00363B";
    /** Texto normal (`LightOnSurface`). */
    public static final String TEXTO = "#0F1B1D";
    /** Texto secundario (`LightOnSurfaceVariant`): pies, notas. */
    public static final String TEXTO_SECUNDARIO = "#4A5C5E";
    /** Bordes suaves (`LightOutlineVariant`). */
    public static final String BORDE = "#BFD2D4";
    /** El fondo, donde empieza el degradado (`LightFondoArriba`). */
    public static final String FONDO = "#F4FCFD";
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
