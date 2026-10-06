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
    val segundosPausaAcumulados: Long = 0,

    /**
     * El nombre del kiosco en que se abrió la jornada, o null si se abrió desde
     * la propia sesión (ADR 033). Para el distintivo del historial.
     */
    val kiosco: String? = null,

    /**
     * La cerró el sistema porque nadie fichó la salida: esa hora es un tope,
     * no un dato. El historial lo dice con un distintivo, para que se pida la
     * corrección.
     */
    val cerradaPorElSistema: Boolean = false
)