package com.nxtime.app.data.dto

/**
 * El horario teórico de un día: `GET /api/v1/cuadrantes/mio` (Fase B1).
 *
 * Lo calcula el servidor entero, con la precedencia ya aplicada: un festivo o
 * una ausencia aprobada mandan sobre una excepción, y una excepción sobre la
 * plantilla. La app no la repite: enseña lo que llega.
 *
 * `origen` viaja como texto y se interpreta con [OrigenDelDia.de], que devuelve
 * null para un valor que esta versión no conozca — mismo criterio que
 * `EstadoHorasExtra.de`.
 *
 * @param entrada la hora a la que debía entrar ("09:00"), si trabaja ese día.
 * @param motivo por qué no se trabaja (festivo, vacaciones) o qué dice la
 *   excepción ("Cambio de turno").
 */
data class DiaTeoricoDTO(
    val fecha: String,
    val origen: String,
    val minutos: Int = 0,
    val entrada: String? = null,
    val tramos: List<TramoTeoricoDTO> = emptyList(),
    val motivo: String? = null,
    val plantilla: String? = null
)

/**
 * Un tramo de trabajo, con las horas ya formateadas por el servidor.
 *
 * @param cruzaMedianoche el turno de noche: termina al día siguiente.
 */
data class TramoTeoricoDTO(
    val horaInicio: String,
    val horaFin: String,
    val minutos: Int = 0,
    val cruzaMedianoche: Boolean = false
)

/** De dónde sale el horario de un día. Los nombres son los del servidor. */
enum class OrigenDelDia {
    /** Festivo o ausencia aprobada. */
    NO_LABORABLE,

    /** Un día que se sale de la plantilla: libre, o con otro horario. */
    EXCEPCION,

    /** Lo que dice la plantilla. Un día sin tramos es libre. */
    CUADRANTE,

    /** La persona no tiene cuadrante ese día. */
    SIN_CUADRANTE;

    companion object {
        fun de(valor: String?): OrigenDelDia? = entries.firstOrNull { it.name == valor }
    }
}
