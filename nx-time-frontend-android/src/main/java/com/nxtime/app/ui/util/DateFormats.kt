package com.nxtime.app.ui.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Todo el formateo de fechas de la app, en un solo sitio.
 *
 * Antes estaba repartido: `ZoneId.of("Europe/Madrid")` aparecía en tres
 * ficheros, los mismos dos formatters estaban duplicados en los dos
 * adaptadores de historial, y el de la pantalla principal se creaba
 * **sin `Locale`**, así que en un móvil configurado en inglés esa hora
 * se formateaba distinto que el resto de la aplicación.
 *
 * La zona es fija y española a propósito: el backend guarda instantes en
 * UTC (`Instant`, desde la Fase 3) y la jornada laboral que representan
 * es la española, no la del sitio donde esté el móvil. Un empleado de
 * viaje debe seguir viendo su jornada en la hora de su centro de
 * trabajo.
 */
object DateFormats {

    val ZONA_ESPANA: ZoneId = ZoneId.of("Europe/Madrid")
    private val ES = Locale.forLanguageTag("es-ES")

    private val FECHA_LARGA = DateTimeFormatter.ofPattern("dd 'de' MMMM, yyyy", ES)
    private val FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy", ES)
    private val HORA = DateTimeFormatter.ofPattern("HH:mm 'h'", ES)

    /** "29 de agosto, 2026" a partir del instante ISO que manda el backend. */
    fun fechaLarga(instanteIso: String?): String = conInstante(instanteIso) {
        FECHA_LARGA.format(it)
    }

    /** "09:00 h" en hora española. */
    fun hora(instanteIso: String?): String = conInstante(instanteIso) {
        HORA.format(it)
    }

    /** "14/12/2026" a partir de una fecha de calendario (las ausencias). */
    fun fechaCorta(fecha: LocalDate?): String =
        fecha?.let { FECHA_CORTA.format(it) } ?: SIN_DATO

    fun fechaCorta(fechaIso: String?): String = try {
        fechaIso?.let { FECHA_CORTA.format(LocalDate.parse(it)) } ?: SIN_DATO
    } catch (e: DateTimeParseException) {
        SIN_DATO
    }

    /**
     * Duración entre dos instantes como "7h 30m".
     *
     * Se calcula sobre los instantes originales, sin pasar por los
     * minutos ya redondeados de cada extremo: es el mismo cuidado que
     * llevó el arreglo del total de los informes en el backend, donde
     * truncar antes de agregar hacía perder hasta 21 minutos al mes.
     */
    fun duracion(entradaIso: String?, salidaIso: String?): String =
        duracionNeta(entradaIso, salidaIso, minutosPausa = 0)

    /**
     * Igual que [duracion], pero descontando las pausas: es el tiempo de
     * trabajo real de una jornada ya cerrada.
     *
     * Descontar aquí y no en la pantalla mantiene la misma cuenta que
     * hacía el historial anterior, que restaba `minutosPausaAcumulados`
     * de la duración bruta. Es una distinción con consecuencias: una
     * jornada de 09:00 a 18:00 con una hora de comida son 8h de trabajo,
     * no 9h, y es el número que acaba en el informe mensual.
     *
     * Si las pausas superaran a la jornada -- dato incoherente que solo
     * puede venir de un fichaje corregido a mano -- se devuelve
     * [SIN_DATO] en lugar de una duración negativa.
     */
    fun duracionNeta(entradaIso: String?, salidaIso: String?, minutosPausa: Long): String = try {
        if (entradaIso == null || salidaIso == null) {
            EN_CURSO
        } else {
            val segundos = Duration.between(
                Instant.parse(entradaIso), Instant.parse(salidaIso)
            ).seconds
            val minutos = segundos / 60 - minutosPausa
            if (minutos < 0) SIN_DATO else formatoHorasMinutos(minutos)
        }
    } catch (e: DateTimeParseException) {
        SIN_DATO
    }

    /** Minutos sueltos como "1h 30m"; se usa también para las pausas. */
    fun minutos(minutos: Long): String = formatoHorasMinutos(minutos)

    private fun formatoHorasMinutos(minutos: Long): String =
        "${minutos / 60}h ${String.format(ES, "%02dm", minutos % 60)}"

    private inline fun conInstante(
        instanteIso: String?,
        formatear: (java.time.ZonedDateTime) -> String
    ): String = try {
        instanteIso?.let { formatear(Instant.parse(it).atZone(ZONA_ESPANA)) } ?: SIN_DATO
    } catch (e: DateTimeParseException) {
        SIN_DATO
    }

    const val SIN_DATO = "--"
    const val EN_CURSO = "En curso"
}
