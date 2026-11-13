package com.nxtime.app.data.dto

/**
 * Panel de empresa: `GET /api/v1/dashboard/empresa` (Fase 10 del backend).
 *
 * `incidenciasAbiertas` son las jornadas que cerró el proceso nocturno
 * por no tener fichaje de salida y que **nadie ha corregido todavía**: es
 * trabajo pendiente real de RRHH, no un número decorativo. Por eso la
 * pantalla lo destaca cuando no es cero.
 *
 * Los minutos vienen enteros, como en el resumen personal.
 */
data class PanelEmpresaDTO(
    val empleadosActivos: Int,
    val minutosMesEmpresa: Long,
    val ausenciasPendientes: Long,
    val incidenciasAbiertas: Long,
    val horasPorEmpleado: List<HorasEmpleadoDTO> = emptyList()
)

data class HorasEmpleadoDTO(
    val usuarioId: Long,
    val nombre: String,
    val minutos: Long,
    /**
     * `false` si ya está de baja: sale porque trabajó este mes. Anulable a
     * propósito: un backend anterior no lo manda, y Gson no aplica los valores
     * por defecto de Kotlin (crea el objeto sin constructor), así que un
     * `Boolean` a secas llegaría a `false` y todos saldrían de baja.
     */
    val activo: Boolean? = null
)

/** Cuerpo de `PATCH /api/v1/gestor/empleados/{id}/estado`. */
data class CambioEstadoEmpleadoRequest(val activo: Boolean)
