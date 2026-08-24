package com.nxtime.app.data.dto

/**
 * DTO que representa un registro de fichaje para el Empleado.
 */
data class Registro(
    val id: Long,
    val horaEntrada: String?,
    val horaSalida: String?,
    val pausas: String?,
    val enPausa: Boolean,

    val minutosPausaAcumulados: Long = 0
)