package com.nxtime.app.data.dto

/**
 * Saldo de vacaciones del usuario (Fase 9 del backend).
 * 'diasDisponibles' ya viene calculado por el backend; la app no lo
 * recalcula para no arriesgarse a mostrar una cifra distinta.
 */
data class SaldoVacacionesDTO(
    val anio: Int,
    val diasTotales: Int,
    val diasConsumidos: Int,
    val diasDisponibles: Int
)
