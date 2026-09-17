package com.nxtime.app.data.dto

/**
 * Añadir una pausa que no se fichó en su momento (ADR 015).
 *
 * El motivo es obligatorio siempre, también cuando se aplica en el acto: no
 * hay tope de duración, y lo que lo hace defendible es que cada pausa
 * añadida lleva escrito por qué.
 */
data class PausaAnadidaRequest(
    val inicio: String,
    val fin: String,
    val motivo: String
)

/**
 * Lo que ha pasado al añadirla.
 *
 * **Lo decide el servidor, no la app**: la jornada abierta o cerrada hoy se
 * aplica en el acto, y la de un día pasado se pide como corrección. Viene
 * uno de los dos objetos, nunca los dos, y `aplicada` dice si el tiempo
 * trabajado YA ha cambiado.
 */
data class PausaAnadidaResultado(
    val aplicada: Boolean,
    val fichaje: Registro? = null,
    val correccion: CorreccionDTO? = null
)

/** Una pausa añadida a posteriori que sigue en pie. */
data class PausaAnadidaDTO(
    val id: Long,
    val inicio: String,
    val fin: String,
    val minutos: Long,
    val motivo: String,
    val creadaPor: UsuarioSimpleDTO? = null,
    val creadaEn: String? = null,
    /** Si entró al aprobarse una corrección, o se aplicó directamente. */
    val porAprobacion: Boolean = false
)
