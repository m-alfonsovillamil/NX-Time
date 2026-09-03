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

    /** Para PINTAR ("Pausa: 0h 26m"). Viene truncado a minutos enteros. */
    val minutosPausaAcumulados: Long = 0,

    /**
     * Para CALCULAR. Los minutos de arriba son `segundos / 60`, así que
     * una pausa de 40 s llega como 0: restar ese 0 contaba 40 segundos de
     * trabajo que no existieron. Lo destapó el cronómetro en vivo, que
     * seguía corriendo durante toda la pausa.
     */
    val segundosPausaAcumulados: Long = 0
)