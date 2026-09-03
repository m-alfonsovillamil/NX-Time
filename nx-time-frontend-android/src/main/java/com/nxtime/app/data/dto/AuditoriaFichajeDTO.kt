package com.nxtime.app.data.dto

/**
 * Una entrada de la línea temporal de `GET /api/v1/auditoria/fichaje/{id}`.
 *
 * `valorAnterior` y `valorNuevo` llegan como **JSON crudo en una cadena**:
 * son la instantánea del fichaje antes y después del cambio, tal y como
 * se guardó en su momento. No se declaran como objeto a propósito -- si
 * la forma de la instantánea cambiara con los años, un DTO tipado
 * fallaría al leer los registros viejos, y una auditoría que no se puede
 * releer no sirve de nada. La pantalla saca de ahí solo lo que enseña.
 */
data class AuditoriaFichajeDTO(
    val id: Long,
    val registroId: Long,
    val usuario: UsuarioSimpleDTO?,
    val modificadoPor: UsuarioSimpleDTO?,
    val accion: String,
    val valorAnterior: String?,
    val valorNuevo: String?,
    val motivo: String?,
    val fechaHora: String?,
    val ip: String?
)

/** Las acciones que registra la auditoría, tal y como viajan en el JSON. */
enum class AccionAuditoria {
    CREACION,
    MODIFICACION,
    CORRECCION,
    ANULACION;

    companion object {
        /**
         * Devuelve `null` si el backend manda una acción que esta versión
         * de la app no conoce. La pantalla la enseña en crudo antes que
         * ocultar una entrada de auditoría: en un registro de
         * cumplimiento normativo, callar una línea es peor que pintarla
         * fea.
         */
        fun de(valor: String?): AccionAuditoria? = entries.firstOrNull { it.name == valor }
    }
}
