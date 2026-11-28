package com.nxtime.app.widget

import com.nxtime.app.data.dto.Registro
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lo que enseña el widget de la jornada (plan P12). Sale de la última jornada
 * que la app ha visto (la que guarda la cola de fichar sin conexión, ADR 054):
 * el widget no habla con el servidor, solo cuenta lo que la app ya sabe.
 */
sealed interface EstadoDelWidget {
    /** Sin sesión en este móvil: el botón lleva a entrar. */
    data object SinSesion : EstadoDelWidget

    /** Ni jornada abierta hoy: el botón lleva a fichar la entrada. */
    data object SinJornada : EstadoDelWidget

    data class Trabajando(val desde: String) : EstadoDelWidget

    data class EnPausa(val desde: String) : EstadoDelWidget
}

/**
 * Una jornada abierta de otro día no cuenta: el servidor la cierra sola, y
 * decir «trabajando desde las 8:05» al día siguiente sería mentir.
 */
fun estadoDelWidget(
    conSesion: Boolean,
    jornada: Registro?,
    hoy: LocalDate,
    zona: ZoneId
): EstadoDelWidget {
    if (!conSesion) return EstadoDelWidget.SinSesion
    if (jornada == null || jornada.horaSalida != null) return EstadoDelWidget.SinJornada
    val entrada = instante(jornada.horaEntrada) ?: return EstadoDelWidget.SinJornada
    if (entrada.atZone(zona).toLocalDate() != hoy) return EstadoDelWidget.SinJornada
    if (jornada.enPausa) {
        val pausa = instante(jornada.inicioPausaActual) ?: entrada
        return EstadoDelWidget.EnPausa(hora(pausa, zona))
    }
    return EstadoDelWidget.Trabajando(hora(entrada, zona))
}

private val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

private fun hora(instante: Instant, zona: ZoneId): String = HORA.format(instante.atZone(zona))

private fun instante(iso: String?): Instant? = try {
    iso?.let(Instant::parse)
} catch (e: java.time.format.DateTimeParseException) {
    null
}
