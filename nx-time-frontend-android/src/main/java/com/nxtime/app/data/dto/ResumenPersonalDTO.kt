package com.nxtime.app.data.dto

/**
 * Mi resumen: `GET /api/v1/dashboard/resumen` (Fase 10 del backend).
 *
 * Los tiempos viajan en MINUTOS enteros a propósito, no en horas
 * decimales: así cada cliente formatea como quiera ("7h 30m") sin
 * arrastrar coma flotante ni decidir cómo redondear.
 *
 * **Los tres totales cuentan solo jornadas CERRADAS.** La consulta del
 * backend filtra por `hora_salida IS NOT NULL`, así que la jornada que
 * está ahora mismo abierta no entra en `minutosHoy`. Quien pinte esto
 * tiene que sumarle el tiempo en curso, o enseñará "Hoy: 0h 00m" a
 * alguien que lleva dos horas fichado.
 */
data class ResumenPersonalDTO(
    val estadoActual: String,
    val minutosHoy: Long,
    val minutosSemana: Long,
    val minutosMes: Long,
    val ausenciasPendientes: Long,
    val saldoVacaciones: SaldoVacacionesDTO?,

    /**
     * La jornada esperada del usuario, en minutos por semana.
     *
     * Sale de `User.horasSemanales` (40 h por defecto, la jornada máxima
     * ordinaria en España). Es lo que permite decir "llevas 32 de 40 h"
     * en vez de un número suelto que no se compara con nada.
     *
     * Por defecto 0, que la pantalla trata como "no hay objetivo" y
     * entonces no pinta ninguna barra de progreso: es preferible a
     * inventarse un 40 que quizá no sea el de este usuario.
     */
    val minutosJornadaSemanal: Long = 0
)
