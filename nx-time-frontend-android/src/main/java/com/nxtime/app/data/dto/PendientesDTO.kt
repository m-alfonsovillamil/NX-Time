package com.nxtime.app.data.dto

/**
 * Los contadores de las bandejas del panel de gestión
 * (`GET /api/v1/dashboard/pendientes`).
 *
 * Cada uno es exactamente lo que se vería al abrir esa bandeja: el servidor
 * los cuenta con los mismos métodos que la llenan. Un 0 también puede
 * significar "no tienes permiso para esa bandeja".
 */
data class PendientesDTO(
    val ausencias: Int = 0,
    val correcciones: Int = 0,
    val horasExtra: Int = 0
)
