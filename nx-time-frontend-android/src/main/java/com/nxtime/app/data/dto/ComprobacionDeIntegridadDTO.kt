package com.nxtime.app.data.dto

/**
 * La última comprobación automática de la cadena de auditoría:
 * `GET /api/v1/auditoria/integridad/ultima`.
 *
 * El backend comprueba cada noche que la traza no se ha manipulado y deja un
 * punto de control. Esto lo trae para poder enseñarlo sin lanzar la
 * verificación entera, que recorre toda la traza.
 *
 * `pendientes` son los movimientos escritos después de esa comprobación. Va
 * junto a la fecha porque sin él la frase engaña: si desde anoche se han
 * fichado doscientas jornadas, lo comprobado es el pasado, no el presente.
 */
data class ComprobacionDeIntegridadDTO(
    val verificadoEn: String,
    val hastaMovimiento: Long,
    val movimientos: Long,
    val comprobados: Long,
    val soloEnlace: Long,
    val pendientes: Long
)
