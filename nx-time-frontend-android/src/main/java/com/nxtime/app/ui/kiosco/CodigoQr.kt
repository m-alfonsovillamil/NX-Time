package com.nxtime.app.ui.kiosco

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * El QR de la tarjeta del kiosco como matriz de módulos (ADR 033).
 *
 * Sin nada de Android a propósito: así se prueba con JUnit a secas, y quien
 * lo pinta (un `Canvas` de Compose) solo tiene que recorrerla. Con el mismo
 * nivel de corrección y margen que el SVG del backend, para que la tarjeta
 * del móvil y la impresa se lean igual de bien.
 */
object CodigoQr {

    /** `modulos[y][x]` es true donde el QR es negro. Incluye el margen blanco. */
    fun modulos(codigo: String): Array<BooleanArray> {
        val matriz = QRCodeWriter().encode(
            codigo, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4)
        )
        return Array(matriz.height) { y -> BooleanArray(matriz.width) { x -> matriz.get(x, y) } }
    }
}
