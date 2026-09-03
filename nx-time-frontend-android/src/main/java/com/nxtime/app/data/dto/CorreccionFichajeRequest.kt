package com.nxtime.app.data.dto

/**
 * Cuerpo de `PATCH /api/v1/fichaje/{id}` (corrección de un fichaje ya
 * cerrado, solo RRHH/ADMIN).
 *
 * Las horas viajan como instantes ISO-8601 en UTC ("2026-09-03T07:00:00Z"),
 * igual que las devuelve el backend. La pantalla las recoge en hora
 * española y las convierte antes de mandarlas.
 *
 * El motivo es obligatorio y lo valida también el servidor: una
 * corrección sin justificar no tiene valor de auditoría, que es
 * justamente para lo que existe este endpoint.
 */
data class CorreccionFichajeRequest(
    val horaEntrada: String,
    val horaSalida: String,
    val motivo: String
)
