package com.nxtime.app.ui.util

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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
    private val FECHA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ES)
    private val MES_Y_ANIO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES)

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
     * Una fecha ISO (`2026-05-15`) como [LocalDate], o `null` si no se
     * puede leer.
     *
     * Sin zona horaria, y ahí está la diferencia con [fechaLocal]: esto
     * son los días de calendario que manda el backend (festivos,
     * ausencias), que ya vienen sin hora. Pasarlos por `Instant` les
     * inventaría una medianoche UTC y en España caerían el día anterior
     * durante el horario de verano.
     */
    fun fechaIso(fechaIso: String?): LocalDate? = try {
        fechaIso?.let { LocalDate.parse(it) }
    } catch (e: DateTimeParseException) {
        null
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
        duracionNeta(entradaIso, salidaIso, segundosPausa = 0)

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
     *
     * La pausa entra en SEGUNDOS y se resta antes de pasar a minutos.
     * Antes entraba en minutos ya truncados, y eso inflaba el total: una
     * pausa de 26 min 40 s viajaba como 26 y los 40 segundos volvían a
     * contarse como trabajo.
     */
    fun duracionNeta(entradaIso: String?, salidaIso: String?, segundosPausa: Long): String = try {
        if (entradaIso == null || salidaIso == null) {
            EN_CURSO
        } else {
            val segundos = Duration.between(
                Instant.parse(entradaIso), Instant.parse(salidaIso)
            ).seconds - segundosPausa
            if (segundos < 0) SIN_DATO else formatoHorasMinutos(segundos / 60)
        }
    } catch (e: DateTimeParseException) {
        SIN_DATO
    }

    /** Minutos sueltos como "1h 30m"; se usa también para las pausas. */
    fun minutos(minutos: Long): String = formatoHorasMinutos(minutos)

    /**
     * "Mayo de 2026", para la cabecera del calendario.
     *
     * En español el mes va en minúscula, pero como título de la pantalla
     * queda raro, así que se pone en mayúscula la inicial -- y con el
     * `Locale` español, no con el del móvil: en turco la mayúscula de "i"
     * no es "I", y ese es el fallo clásico de `uppercase()` sin locale.
     */
    fun mesYAnio(periodo: YearMonth): String {
        val texto = MES_Y_ANIO.format(periodo)
        return texto.replaceFirstChar { it.titlecase(ES) }
    }

    /**
     * La inicial de un día de la semana ("L", "M", "X"...), en español.
     *
     * Va aquí y no en la pantalla por lo mismo que todo lo demás de esta
     * clase: `getDisplayName` sin `Locale` explícito usa el del móvil, y
     * en un teléfono en inglés la fila de la rejilla saldría "M T W T F
     * S S" con el resto de la pantalla en español.
     */
    fun inicialDelDia(dia: DayOfWeek): String =
        dia.getDisplayName(java.time.format.TextStyle.NARROW, ES).uppercase(ES)

    /**
     * Segundos netos trabajados en la jornada abierta, "02:14:38".
     *
     * Se cuenta en SEGUNDOS de principio a fin, sin pasar por minutos
     * intermedios: es el mismo cuidado que hizo falta en el informe
     * mensual del backend, donde truncar antes de agregar perdía hasta
     * 21 minutos al mes. Un cronómetro que trunque cada vuelta se queda
     * congelado o salta de dos en dos.
     *
     * Negativo no se pinta: solo puede salir de un reloj del móvil
     * atrasado respecto al servidor, y un "-00:00:12" asusta más que un
     * cero.
     */
    fun cronometro(segundos: Long): String {
        val s = segundos.coerceAtLeast(0)
        return String.format(ES, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /**
     * Segundos netos que lleva una jornada todavía abierta: lo que va de
     * la entrada hasta ahora, menos las pausas ya terminadas.
     *
     * Descuenta **segundos** de pausa y no minutos a propósito. Restar
     * `minutosPausaAcumulados * 60` parecía equivalente y no lo es: ese
     * campo viaja truncado, así que una pausa de 40 s vale 0 minutos y
     * el cronómetro se comía la pausa entera. Es el mismo patrón que ya
     * mordió dos veces en el backend -- truncar antes de agregar.
     *
     * Solo vale mientras se está TRABAJANDO. Durante una pausa la cuenta
     * se dispararía, porque la pausa en curso aún no está acumulada: el
     * backend la suma al reanudar.
     */
    fun segundosTrabajados(
        entradaIso: String?,
        segundosPausa: Long,
        ahora: Instant = Instant.now()
    ): Long = try {
        entradaIso?.let {
            Duration.between(Instant.parse(it), ahora).seconds - segundosPausa
        } ?: 0L
    } catch (e: DateTimeParseException) {
        0L
    }

    private fun formatoHorasMinutos(minutos: Long): String =
        "${minutos / 60}h ${String.format(ES, "%02dm", minutos % 60)}"

    /** "03/09/2026 09:30" para la línea temporal de auditoría. */
    fun fechaYHora(instanteIso: String?): String = conInstante(instanteIso) {
        FECHA_Y_HORA.format(it)
    }

    /**
     * La hora local española de un instante, como par (hora, minuto).
     *
     * La usa el formulario de corrección para partir de la hora que el
     * usuario ve en pantalla, no de la UTC que viaja por debajo.
     */
    fun horaYMinutoLocal(instanteIso: String?): Pair<Int, Int>? = try {
        instanteIso?.let {
            val local = Instant.parse(it).atZone(ZONA_ESPANA)
            local.hour to local.minute
        }
    } catch (e: DateTimeParseException) {
        null
    }

    /** El día de calendario español al que pertenece un instante. */
    fun fechaLocal(instanteIso: String?): LocalDate? = try {
        instanteIso?.let { Instant.parse(it).atZone(ZONA_ESPANA).toLocalDate() }
    } catch (e: DateTimeParseException) {
        null
    }

    /**
     * Días de calendario que separan la salida de la entrada. 0 cuando
     * la jornada empieza y acaba el mismo día.
     *
     * Existe por el turno de noche: una jornada de 22:52 a 00:29 se
     * enseñaba como "Entrada 22:52 h / Salida 00:29 h" sin decir en
     * ninguna parte que la salida es del día siguiente, y así leída
     * parece una jornada de veintidós horas hacia atrás. Con el
     * calendario delante se nota todavía más.
     *
     * Se comparan **días de calendario españoles**, no horas: restar
     * instantes daría 0 para una jornada de 23:00 a 00:30 (hora y media)
     * cuando el día sí ha cambiado, que es justo lo que hay que decir.
     */
    fun diasDeDiferencia(entradaIso: String?, salidaIso: String?): Int {
        val entrada = fechaLocal(entradaIso) ?: return 0
        val salida = fechaLocal(salidaIso) ?: return 0
        return (salida.toEpochDay() - entrada.toEpochDay()).toInt().coerceAtLeast(0)
    }

    /**
     * El camino de vuelta: una fecha y una hora **españolas** al instante
     * ISO en UTC que espera el backend.
     *
     * Es la conversión que hay que hacer sí o sí antes de mandar una
     * corrección. Componer la cadena a mano con la hora local produciría
     * un fichaje desplazado una o dos horas según la época del año, y
     * encima quedaría firmado en la auditoría.
     */
    fun aInstanteIso(fecha: LocalDate, hora: Int, minuto: Int): String =
        fecha.atTime(hora, minuto).atZone(ZONA_ESPANA).toInstant().toString()

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
