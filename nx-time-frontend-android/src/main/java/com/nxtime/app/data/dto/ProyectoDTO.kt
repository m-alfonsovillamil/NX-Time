package com.nxtime.app.data.dto

/**
 * Un proyecto de la empresa.
 *
 * @param asignados cuánta gente ha pasado por él (histórico, no solo
 *   quien está hoy). Viaja con el listado porque es lo que decide si se
 *   puede ofrecer el botón de borrar: uno con asignaciones no se borra,
 *   se cierra.
 * @param activo si sigue abierto. **No es lo mismo que tener
 *   `fechaFin`**: un proyecto puede haber terminado en su fecha prevista
 *   o haberse cancelado sin fecha.
 */
data class ProyectoDTO(
    val id: Long,
    val codigo: String,
    val nombre: String,
    val descripcion: String? = null,
    val fechaInicio: String,
    val fechaFin: String? = null,
    val activo: Boolean = true,
    val asignados: Long = 0
)

/**
 * Quién está en qué proyecto y con qué vigencia.
 *
 * Sirve para las dos pantallas que miran esto desde lados opuestos: la
 * ficha del proyecto ("quién ha pasado por aquí") y el perfil de la
 * persona ("en qué he estado").
 *
 * @param fechaFin null = sigue asignado.
 * @param vigente lo resuelve el servidor para no obligar a la app a
 *   comparar con "hoy", que además tendría que hacer en la zona correcta.
 */
data class AsignacionProyectoDTO(
    val id: Long,
    val usuarioId: Long,
    val usuario: String,
    val proyectoId: Long,
    val proyectoCodigo: String,
    val proyectoNombre: String,
    val fechaInicio: String,
    val fechaFin: String? = null,
    val vigente: Boolean = false
)

/**
 * El detalle de un proyecto: sus datos, el histórico de asignaciones y
 * las horas del mes consultado.
 *
 * Ojo: `asignaciones` y `horas` **no son la misma gente**. En la primera
 * está todo el histórico; en la segunda, solo quien trabajó ese mes.
 */
data class DetalleProyectoDTO(
    val proyecto: ProyectoDTO,
    val asignaciones: List<AsignacionProyectoDTO> = emptyList(),
    val anio: Int,
    val mes: Int,
    val horas: List<HorasEmpleadoDTO> = emptyList()
)

// `horas` reutiliza HorasEmpleadoDTO, que ya existe en PanelEmpresaDTO
// con exactamente los mismos tres campos: declararlo otra vez aquí solo
// habría creado dos tipos idénticos que hay que mantener a la vez.

/** Horas por proyecto del mes, para el panel de empresa. */
data class HorasPorProyectoDTO(
    val anio: Int,
    val mes: Int,
    val proyectos: List<HorasProyectoDTO> = emptyList()
)

data class HorasProyectoDTO(
    val proyectoId: Long,
    val codigo: String,
    val nombre: String,
    val minutos: Long
)

/** Alta y edición. Sin `activo`: cerrar o reabrir va por su endpoint. */
data class ProyectoRequest(
    val codigo: String,
    val nombre: String,
    val descripcion: String? = null,
    val fechaInicio: String,
    val fechaFin: String? = null
)

data class AsignarProyectoRequest(
    val usuarioId: Long,
    val fechaInicio: String,
    val fechaFin: String? = null
)

data class EstadoProyectoRequest(val activo: Boolean)

/** Sacar a alguien del proyecto: pone la fecha de fin, no borra nada. */
data class FinalizarAsignacionRequest(val fechaFin: String)
