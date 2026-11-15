package com.nxtime.app.data.dto

/**
 * Quién está trabajando ahora y quién no ha fichado hoy
 * (GET /api/v1/gestor/equipo-ahora; plan N, paso N7).
 *
 * El estado va como texto y no como enum a propósito: un estado nuevo del
 * servidor no puede romper el panel de una versión vieja de la app (Gson deja
 * a null el valor de un enum que no conoce, ver ADR 058).
 */
data class EquipoAhoraDTO(
    val fecha: String? = null,
    val trabajando: Int = 0,
    val enPausa: Int = 0,
    val terminada: Int = 0,
    val sinFichar: Int = 0,
    val conRetraso: Int = 0,
    val noLaborable: Int = 0,
    val personas: List<PersonaAhoraDTO> = emptyList()
)

data class PersonaAhoraDTO(
    val usuarioId: Long = 0,
    val nombre: String = "",
    /** TRABAJANDO, EN_PAUSA, TERMINADA, SIN_FICHAR o NO_LABORABLE. */
    val estado: String? = null,
    val desde: String? = null,
    val minutosHoy: Long = 0,
    val motivo: String? = null,
    val entradaPrevista: String? = null,
    val conRetraso: Boolean = false,
    val modalidad: String? = null
) {
    companion object {
        const val TRABAJANDO = "TRABAJANDO"
        const val EN_PAUSA = "EN_PAUSA"
        const val TERMINADA = "TERMINADA"
        const val SIN_FICHAR = "SIN_FICHAR"
        const val NO_LABORABLE = "NO_LABORABLE"
    }
}
