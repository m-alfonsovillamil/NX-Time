package com.nxtime.app.data.dto

/**
 * Una incidencia de cuadrante: `GET /api/v1/incidencias/...` (Fase B2).
 *
 * **Se detecta, no se imputa.** El barrido nocturno compara lo fichado con el
 * cuadrante y anota el retraso, la salida anticipada o la ausencia; ninguna
 * descuenta nada. Lo que sigue es una conversación: la persona la explica y
 * alguien del equipo de gestión la acepta o la rechaza.
 *
 * `horaPrevista` llega ya formateada ("09:00") porque es la hora del
 * cuadrante, no un instante: formatearla aquí con la zona del teléfono podría
 * moverla. `horaReal` sí es un instante, y es null en una ausencia.
 *
 * `minutos` es lo que se llegó tarde o se salió antes; en una ausencia, lo que
 * duraba la jornada prevista.
 *
 * `tipo` y `estado` viajan como texto por lo mismo que [AvisoDTO.tipo].
 */
data class IncidenciaDTO(
    val id: Long,
    val usuarioId: Long,
    val usuario: String,
    val fecha: String,

    /** "RETRASO", "SALIDA_ANTICIPADA" o "AUSENCIA". */
    val tipo: String,
    val minutos: Int,
    val horaPrevista: String,
    val horaReal: String? = null,

    /** "PENDIENTE", "JUSTIFICADA", "ACEPTADA" o "RECHAZADA". */
    val estado: String,
    val justificacion: String? = null,
    val justificadaEn: String? = null,
    val comentarioResolucion: String? = null,
    val resueltaPor: String? = null,
    val resueltaEn: String? = null
)

/** La explicación de quien tiene la incidencia. No puede ir vacía. */
data class JustificarIncidenciaRequest(val texto: String)

/**
 * La decisión sobre una incidencia. Rechazar exige comentario, aceptar no:
 * el mismo criterio que en horas extra, al revés de signo.
 */
data class ResolverIncidenciaRequest(
    val aceptar: Boolean,
    val comentario: String? = null
)
