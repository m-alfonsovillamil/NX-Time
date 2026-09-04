package com.nxtime.nxtime.service.impl;

/**
 * Deduce el tipo real de un fichero por sus PRIMEROS BYTES (Fase B2).
 *
 * No se mira la extensión ni el {@code Content-Type} de la petición
 * porque los dos los elige quien sube: renombrar {@code virus.exe} a
 * {@code cv.pdf} y declarar {@code application/pdf} cuesta nada. Los
 * primeros bytes son lo que de verdad va a interpretar quien lo abra.
 *
 * Detecta solo los tres formatos que la aplicación acepta. No pretende
 * ser una librería de detección: si algún día hacen falta más, el sitio
 * es este o Apache Tika, no repartir comprobaciones por los servicios.
 */
final class ContentTypeDetector {

    private ContentTypeDetector() {
    }

    /** "%PDF-", cabecera obligatoria de todo PDF. */
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D};

    /** Todo JPEG empieza por FF D8 FF. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** \x89PNG\r\n\x1a\n */
    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    /**
     * @return el MIME reconocido, o null si no es ninguno de los que
     *     acepta la aplicación. Null significa "no lo acepto", no
     *     "no lo sé": quien llama lo traduce a un 400.
     */
    static String detectar(byte[] contenido) {
        if (empiezaPor(contenido, PDF)) {
            return "application/pdf";
        }
        if (empiezaPor(contenido, JPEG)) {
            return "image/jpeg";
        }
        if (empiezaPor(contenido, PNG)) {
            return "image/png";
        }
        return null;
    }

    private static boolean empiezaPor(byte[] contenido, byte[] firma) {
        if (contenido == null || contenido.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (contenido[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
