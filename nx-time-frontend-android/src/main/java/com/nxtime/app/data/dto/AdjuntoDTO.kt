package com.nxtime.app.data.dto

/**
 * Un adjunto (CV o foto), **sin su contenido**: los bytes se piden
 * aparte, en su propio endpoint.
 *
 * @param mime el real, deducido en el servidor de los primeros bytes del
 *   fichero. En una foto es siempre `image/jpeg`, porque el servidor la
 *   reescala a JPEG sea cual sea el original.
 * @param tamanoBytes el de lo GUARDADO, que en una foto no es el del
 *   fichero que se eligió.
 */
data class AdjuntoDTO(
    val id: Long,
    val tipo: String,
    val nombreOriginal: String,
    val mime: String,
    val tamanoBytes: Long,
    val subidoEn: String
)
