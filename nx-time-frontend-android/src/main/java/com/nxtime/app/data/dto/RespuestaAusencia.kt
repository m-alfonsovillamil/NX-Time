package com.nxtime.app.data.dto

/**
 * DTO que la app Android RECIBIRÁ del backend al consultar el historial.
 * Se ha añadido la propiedad 'usuario' para que el GestorAusenciasAdapter pueda acceder al nombre del empleado.
 */
data class RespuestaAusencia(
    val id: Long,
    val fechaInicio: String,
    val fechaFin: String,
    val tipo: TipoAusencia,
    val estado: EstadoAusencia,
    val motivo: String?,

    val usuario: UsuarioSimpleDTO
)