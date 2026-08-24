package com.nxtime.nxtime.dto

/**
 * DTO para la respuesta del historial de fichajes del equipo.
 */
data class RegistroEquipoDTO(
    val id: Long,
    val horaEntrada: String,
    val horaSalida: String?,
    val fecha: String,
    val usuario: UsuarioSimpleDTO,

    val minutosPausaAcumulados: Long
)