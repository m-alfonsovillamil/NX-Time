package com.nxtime.app.data.dto

/**
 * Una solicitud de corrección de fichaje (Fase E).
 *
 * `puedoResolver` y `puedoDisputar` **los calcula el servidor para quien
 * pregunta**, y son la razón de que la app no tenga que saber nada de la
 * regla: quién puede resolver una corrección depende de quién la pidió
 * —el dueño del fichaje decide cuando se la proponen, y un gestor
 * cuando la pide el empleado—, y replicar eso aquí sería tener dos
 * copias de lo más delicado de la fase, condenadas a discrepar.
 *
 * `estado` viaja como texto y no como enum por lo mismo que
 * [AvisoDTO.tipo]: un valor que esta versión no conozca quedaría a
 * `null` sin que Gson avise. Se traduce con
 * [com.nxtime.app.ui.correcciones.EstadoCorreccion].
 */
data class CorreccionDTO(
    val id: Long,
    val fichajeId: Long,

    /** De quién es el fichaje. No tiene por qué ser el solicitante. */
    val empleado: UsuarioSimpleDTO,
    val solicitante: UsuarioSimpleDTO,

    /** Lo que dice el fichaje ahora, para poder comparar sin salir de la pantalla. */
    val horaEntradaActual: String? = null,
    val horaSalidaActual: String? = null,
    val horaEntradaPropuesta: String,
    val horaSalidaPropuesta: String,

    val motivo: String,
    val estado: String,

    val aprobador: UsuarioSimpleDTO? = null,
    val fechaResolucion: String? = null,
    val comentarioResolucion: String? = null,
    val motivoDisputa: String? = null,
    val creadoEn: String? = null,

    val puedoResolver: Boolean = false,
    val puedoDisputar: Boolean = false
)

/** Aprobar o rechazar. Al rechazar, el comentario es obligatorio. */
data class ResolverCorreccionRequest(val aprobada: Boolean, val comentario: String? = null)

/** No aceptar una corrección que te han propuesto. */
data class DisputaRequest(val motivo: String)
