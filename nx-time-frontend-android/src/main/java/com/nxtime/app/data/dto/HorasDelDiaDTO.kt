package com.nxtime.app.data.dto

/**
 * Un día del gráfico de horas (`GET /api/v1/dashboard/horas-por-dia`).
 *
 * `minutosTrabajados` solo cuenta jornadas **cerradas**: la abierta la suma
 * la app, que ya lleva el cronómetro (ver `DetalleDeTiempo.barrasCon`).
 * `minutosEsperados` es 0 en fin de semana, festivo o ausencia aprobada, y
 * `festivo`/`ausencia` dicen por qué.
 */
data class HorasDelDiaDTO(
    val fecha: String,
    val minutosTrabajados: Long,
    val minutosEsperados: Long,
    val festivo: String? = null,
    val ausencia: String? = null
)
