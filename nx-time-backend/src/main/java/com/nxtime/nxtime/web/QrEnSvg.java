package com.nxtime.nxtime.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.Map;

/**
 * Un QR como SVG, listo para pintar en la web, en la app o en papel: la
 * tarjeta del kiosco (ADR 033) y el alta del segundo factor (ADR 051).
 *
 * Se escribe a mano a partir de la matriz de ZXing, un rectángulo por módulo
 * oscuro en un solo {@code path}: así ningún cliente necesita una librería de
 * QR, y el SVG escala sin perder nitidez al imprimirlo.
 */
public final class QrEnSvg {

    private QrEnSvg() {
    }

    /**
     * @param etiqueta lo que dice un lector de pantalla; va en un atributo, así
     *   que no puede llevar comillas
     */
    public static String de(String contenido, String etiqueta) {
        try {
            BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 4));
            int ancho = matriz.getWidth();
            int alto = matriz.getHeight();
            StringBuilder trazo = new StringBuilder();
            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    if (matriz.get(x, y)) {
                        trazo.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                    }
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + ancho + " " + alto
                    + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"" + etiqueta.replace("\"", "") + "\">"
                    + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                    + "<path fill=\"#000\" d=\"" + trazo + "\"/></svg>";
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el QR", e);
        }
    }
}
