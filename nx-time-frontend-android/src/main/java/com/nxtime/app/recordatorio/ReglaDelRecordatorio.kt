package com.nxtime.app.recordatorio

import com.nxtime.app.data.dto.Registro
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** Cuál de los dos avisos es. */
enum class TipoDeAviso { ENTRADA, SALIDA }

/**
 * Cuándo toca avisar, sin nada de Android dentro.
 *
 * Vive aparte del `Worker` a propósito: es la única parte con decisiones y
 * así se prueba en la JVM, sin emulador. El `Worker` se queda con lo que
 * no se puede probar sin sistema operativo -- pedir el fichaje activo y
 * lanzar la notificación.
 */
object ReglaDelRecordatorio {

    /**
     * Hay jornada abierta si existe un registro y **no** tiene hora de
     * salida. Es el mismo criterio que usa `FicharViewModel` para decidir
     * que no hay jornada; si algún día cambia allí, tiene que cambiar aquí.
     */
    fun jornadaAbierta(registro: Registro?): Boolean =
        registro != null && registro.horaSalida == null

    /** De lunes a viernes. Un aviso el domingo solo enseña a ignorarlos. */
    fun esLaborable(dia: DayOfWeek): Boolean =
        dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY

    /**
     * La decisión, y lo importante es lo que **no** avisa: a la hora de
     * entrada calla si ya has fichado, y a la de salida calla si ya has
     * cerrado. Un recordatorio que llega cuando la cosa ya está hecha es
     * exactamente lo que hace que se silencien las notificaciones de una
     * aplicación.
     */
    fun toca(tipo: TipoDeAviso, hayJornadaAbierta: Boolean, dia: DayOfWeek): Boolean {
        if (!esLaborable(dia)) return false
        return when (tipo) {
            TipoDeAviso.ENTRADA -> !hayJornadaAbierta
            TipoDeAviso.SALIDA -> hayJornadaAbierta
        }
    }

    /** "HH:mm" → hora, o null si no tiene ese formato. */
    fun hora(texto: String): LocalTime? = try {
        LocalTime.parse(texto)
    } catch (_: DateTimeParseException) {
        null
    }

    fun esHoraValida(texto: String): Boolean = hora(texto) != null

    /**
     * Minutos hasta la próxima vez que sean las [objetivo].
     *
     * Si esa hora ya pasó hoy, cuenta hasta mañana. Devuelve **al menos 1**
     * porque un retardo de 0 haría que WorkManager lanzara el aviso al
     * instante al programarlo: quien activa el recordatorio a las 09:30 no
     * espera que le salte una notificación en ese mismo momento.
     */
    fun minutosHasta(objetivo: LocalTime, ahora: LocalTime): Long {
        val minutos = Duration.between(ahora, objetivo).toMinutes()
        val ajustados = if (minutos <= 0) minutos + MINUTOS_POR_DIA else minutos
        return maxOf(1L, ajustados)
    }

    private const val MINUTOS_POR_DIA = 24L * 60L
}
