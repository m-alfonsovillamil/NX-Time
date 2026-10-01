package com.nxtime.app.data.dto

/**
 * Fichar en un kiosco (ADR 033): la tablet compartida de la entrada, donde se
 * ficha pasando la tarjeta QR o eligiendo el nombre y tecleando el PIN. Desde la
 * app solo se prepara: el PIN y la tarjeta se gestionan en el perfil; la
 * pantalla de la tablet es la web.
 *
 * @param pinBloqueadoHasta instante ISO hasta el que el PIN está bloqueado por
 *     demasiados intentos, o null
 */
data class EstadoKioscoDTO(
    val tienePin: Boolean = false,
    val tieneTarjeta: Boolean = false,
    val pinBloqueadoHasta: String? = null
)

/** Lo que va dentro del QR ([codigo]) y el QR dibujado en SVG, que la app no usa: lo pinta ella. */
data class TarjetaKioscoDTO(
    val usuarioId: Long,
    val nombre: String?,
    val codigo: String,
    val svg: String? = null
)

data class PinKioscoRequest(val pin: String)
