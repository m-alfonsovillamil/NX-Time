package com.nxtime.app.data.dto

/**
 * Una oferta interna (Fase H).
 *
 * Tres campos llegan **resueltos por el servidor** en vez de deducirse
 * aquí, y los tres por lo mismo: que la app no reimplemente una regla
 * que ya vive en el backend.
 *
 * - `admiteCandidaturas` son DOS condiciones (publicada y en plazo); una
 *   app que solo mirara el estado ofrecería presentarse a una oferta
 *   cuyo plazo venció.
 * - `plazoVencido` permite decir *por qué* no se admite, que no es lo
 *   mismo que no admitir.
 * - `yaMePresente` es lo que convierte el botón en "Ver mi candidatura".
 *
 * `candidaturas` llega **null** a quien no puede valorarlas: cuántos
 * compañeros han optado a un puesto no es dato para el resto de la
 * plantilla. Null y no cero, como el contador de denuncias del panel.
 */
data class OfertaDTO(
    val id: Long,
    val titulo: String,
    val descripcion: String,
    val puesto: String? = null,
    val departamento: String? = null,
    val publicadaPor: String,
    /** "BORRADOR", "ABIERTA" o "CERRADA". */
    val estado: String,
    val fechaPublicacion: String? = null,
    val fechaCierre: String? = null,
    val admiteCandidaturas: Boolean,
    val plazoVencido: Boolean,
    val yaMePresente: Boolean,
    val candidaturas: Long? = null
)

/**
 * Crear o editar una oferta.
 *
 * **No lleva estado**: publicar es un endpoint aparte. Con el estado
 * aquí dentro, guardar un cambio de redacción publicaría la oferta a
 * toda la plantilla sin querer.
 */
data class OfertaRequest(
    val titulo: String,
    val descripcion: String,
    val puesto: String? = null,
    val departamentoId: Long? = null,
    /** ISO-8601 (yyyy-MM-dd), o null si no tiene plazo. */
    val fechaCierre: String? = null
)

/** Publicar, retirar al borrador o cerrar. */
data class CambiarEstadoOfertaRequest(val estado: String)

/**
 * Una candidatura (Fase H).
 *
 * `cvAdjuntoId` es el adjunto **congelado** al presentarse, no el CV
 * actual de la persona: es lo que hay que descargar para leer lo que se
 * presentó. Puede apuntar a un adjunto que ya no está vigente, y esa es
 * exactamente la razón de que exista esa distinción.
 *
 * `puedoValorar` llega resuelto y es falso sobre la propia: un GESTOR
 * puede optar a una vacante como cualquiera, y entonces esa candidatura
 * la decide otro.
 */
data class CandidaturaDTO(
    val id: Long,
    val ofertaId: Long,
    val ofertaTitulo: String,
    val usuarioId: Long,
    val candidato: String,
    val carta: String? = null,
    /** "RECIBIDA", "EN_PROCESO", "DESCARTADA" o "SELECCIONADA". */
    val estado: String,
    val resueltaPor: String? = null,
    val fechaResolucion: String? = null,
    val comentario: String? = null,
    val creadoEn: String,
    val cvAdjuntoId: Long,
    val cvNombre: String,
    val puedoValorar: Boolean
)

/**
 * Presentarse a una oferta.
 *
 * **No lleva el CV**: lo adjunta el servidor, y es el vigente de quien
 * se presenta. Dejarlo elegir permitiría mandar el de otra persona o una
 * versión ya retirada.
 */
data class CandidaturaRequest(val carta: String? = null)

/** Valorar una candidatura. El comentario es obligatorio al descartar. */
data class ValorarCandidaturaRequest(
    val estado: String,
    val comentario: String? = null
)
