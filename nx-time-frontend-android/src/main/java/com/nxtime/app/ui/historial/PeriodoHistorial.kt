package com.nxtime.app.ui.historial

import com.nxtime.app.data.dto.Registro
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

/**
 * Qué parte del historial se está mirando.
 *
 * [Recientes] es lo de siempre (los últimos 200, sin fechas), y es el valor
 * por defecto: quien abre el historial quiere ver lo último sin elegir nada.
 */
sealed interface PeriodoHistorial {
    data object Recientes : PeriodoHistorial
    data object EstaSemana : PeriodoHistorial
    data object EsteMes : PeriodoHistorial
    data object MesAnterior : PeriodoHistorial
    data class Elegido(val desde: LocalDate, val hasta: LocalDate) : PeriodoHistorial

    /**
     * Los dos días (incluidos) que se piden al servidor, o `null` para
     * [Recientes]. La semana empieza en lunes, como en España y como en el
     * detector de horas extra.
     */
    fun rango(hoy: LocalDate): Pair<LocalDate, LocalDate>? = when (this) {
        Recientes -> null
        EstaSemana -> hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
        EsteMes -> hoy.withDayOfMonth(1) to hoy.with(TemporalAdjusters.lastDayOfMonth())
        MesAnterior -> hoy.minusMonths(1).let { it.withDayOfMonth(1) to it.with(TemporalAdjusters.lastDayOfMonth()) }
        is Elegido -> desde to hasta
    }

    companion object {
        /**
         * Segundos netos de las jornadas **cerradas** de la lista. La abierta
         * no suma: su total cambia cada segundo y la cabecera diría una cifra
         * que ya no es verdad al leerla.
         */
        fun segundosNetos(registros: List<Registro>): Long = registros.sumOf { registro ->
            val entrada = registro.horaEntrada ?: return@sumOf 0L
            val salida = registro.horaSalida ?: return@sumOf 0L
            try {
                (Duration.between(Instant.parse(entrada), Instant.parse(salida)).seconds -
                    registro.segundosPausaAcumulados).coerceAtLeast(0)
            } catch (e: DateTimeParseException) {
                0L
            }
        }
    }
}
