package com.nxtime.app.data.dto

/**
 * DTO que representa un registro de fichaje para el Empleado.
 *
 * Refleja exactamente el `TimeEntryResponse` del backend. Tenía además
 * un campo `pausas: String?` que ese record no envía nunca, así que Gson
 * lo dejaba siempre a null: se ha quitado para que el DTO no prometa un
 * dato que no existe.
 */
data class Registro(
    val id: Long,
    val horaEntrada: String?,
    val horaSalida: String?,
    val enPausa: Boolean,

    val minutosPausaAcumulados: Long = 0
)