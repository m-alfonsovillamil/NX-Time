package com.nxtime.app.data.dto

/**
 * DTO que la app Android RECIBIRÁ del backend al consultar el historial.
 * Se ha añadido la propiedad 'usuario' para que el GestorAusenciasAdapter pueda acceder al nombre del empleado.
 *
 * Desde la Fase 9 del backend llegan además la trazabilidad de la
 * resolución (aprobadoPor / fechaResolucion / comentarioResolucion, los
 * tres nulos mientras siga PENDIENTE) y 'diasHabiles', los días que
 * realmente consume la ausencia sin contar fines de semana ni festivos.
 * Todos son opcionales en Kotlin para no romper si el backend aún no los
 * envía.
 */
data class RespuestaAusencia(
    val id: Long,
    val fechaInicio: String,
    val fechaFin: String,
    val tipo: TipoAusencia,
    val estado: EstadoAusencia,
    val motivo: String?,

    val usuario: UsuarioSimpleDTO,

    val aprobadoPor: UsuarioSimpleDTO? = null,
    val fechaResolucion: String? = null,
    val comentarioResolucion: String? = null,
    val diasHabiles: Int = 0
)
