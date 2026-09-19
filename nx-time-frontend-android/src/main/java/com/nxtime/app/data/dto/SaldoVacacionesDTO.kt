package com.nxtime.app.data.dto

/**
 * Saldo de vacaciones del usuario (Fase 9 del backend).
 *
 * 'diasDisponibles' ya viene calculado por el backend; la app no lo
 * recalcula para no arriesgarse a mostrar una cifra distinta. Desde el
 * arreglo del saldo descuenta también 'diasPendientes': quien tiene días
 * pedidos y sin resolver no los tiene libres, y verlos aparte explica por
 * qué le quedan menos de los que esperaba.
 *
 * 'diasPendientes' tiene valor por defecto porque un servidor anterior al
 * arreglo no lo manda, y sin él Gson dejaría el campo a cero igualmente.
 */
data class SaldoVacacionesDTO(
    val anio: Int,
    val diasTotales: Int,
    val diasConsumidos: Int,
    val diasPendientes: Int = 0,
    val diasDisponibles: Int
)
