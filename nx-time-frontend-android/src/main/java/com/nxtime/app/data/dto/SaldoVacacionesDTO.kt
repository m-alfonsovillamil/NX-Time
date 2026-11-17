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
    // Con decimales desde el ADR 063: un medio día de vacaciones gasta 0,5.
    val diasTotales: Double,
    val diasConsumidos: Double,
    val diasPendientes: Double = 0.0,
    val diasDisponibles: Double,
    /** Los que vienen del año anterior, ya dentro de [diasTotales] (ADR 060). */
    val diasArrastrados: Double = 0.0,
    /** "AAAA-MM-DD" hasta cuándo valen, o null. */
    val arrastreCaducaEl: String? = null
)
