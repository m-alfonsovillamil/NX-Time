package com.nxtime.app.data.dto

/**
 * DTO que representa un registro de fichaje del equipo (para el Gestor).
 */
data class RegistroEquipoDTO(
    val id: Long,
    val horaEntrada: String,
    val horaSalida: String?,
    val fecha: String,
    val usuario: UsuarioSimpleDTO,

    /** Para pintar. Truncado a minutos enteros. */
    val minutosPausaAcumulados: Long = 0,

    /** Para calcular. Ver el mismo campo en [Registro]. */
    val segundosPausaAcumulados: Long = 0
)