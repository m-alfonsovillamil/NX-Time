package com.nxtime.app.data.dto

/**
 * Un exceso de jornada detectado (Fase F).
 *
 * **Es un aviso, no una imputación.** El proceso nocturno del servidor
 * detecta que una jornada o una semana se pasó del umbral; lo que decide
 * si esas horas son extra de verdad es la revisión de una persona. Solo
 * lo `ACEPTADO` consume la bolsa anual.
 *
 * `fecha` es el día si el tipo es `DIARIA` y el **lunes** de la semana si
 * es `SEMANAL`; `fechaFin` viaja ya calculada por el servidor para que la
 * pantalla no tenga que saber esa regla.
 *
 * `minutosEsperados` es el listón contra el que se comparó, y sin él un
 * aviso semanal no se puede entender: en una semana con un puente son 30
 * h y no 37,5, y el número por sí solo no lo dice.
 *
 * `tipo` y `estado` viajan como texto y no como enum por lo mismo que
 * [AvisoDTO.tipo]: un valor que esta versión no conozca quedaría a `null`
 * sin que Gson avise.
 */
data class HorasExtraDTO(
    val id: Long,
    val usuarioId: Long,
    val usuario: String,

    /** "DIARIA" o "SEMANAL". */
    val tipo: String,
    val fecha: String,
    val fechaFin: String,

    val minutosExtra: Int,
    val minutosEsperados: Int,

    /** Solo en los diarios: el fichaje que cruzó el umbral. */
    val registroId: Long? = null,

    /** "ABIERTO", "JUSTIFICADO" o "ACEPTADO". */
    val estado: String,
    val justificacion: String? = null,
    val revisadoPor: String? = null,
    val fechaRevision: String? = null
)

/**
 * La bolsa anual del art. 35.2 ET.
 *
 * Todo en minutos: "1,75 h" en una pantalla se lee mal, y componer
 * "1 h 45 min" desde un decimal es una cuenta que se hace mal de cabeza.
 *
 * `alLimite` llega resuelto porque el umbral de aviso (el 80 % del tope)
 * es una regla de negocio, no una decisión de presentación: tiene que
 * significar lo mismo aquí que en el proceso que manda el correo.
 */
data class BolsaHorasExtraDTO(
    val anio: Int,
    val minutosTope: Int,
    val minutosConsumidos: Int,
    val minutosDisponibles: Int,
    /** Avisos sin revisar: horas que PODRÍAN acabar contando. */
    val avisosAbiertos: Long,
    val alLimite: Boolean
)

/**
 * La decisión sobre un aviso.
 *
 * `aceptar` false exige justificación: aceptar unas horas que el reloj ya
 * ha medido no necesita explicación, pero decir que once horas trabajadas
 * no cuentan sí.
 */
data class RevisarHorasExtraRequest(
    val aceptar: Boolean,
    val justificacion: String? = null
)
