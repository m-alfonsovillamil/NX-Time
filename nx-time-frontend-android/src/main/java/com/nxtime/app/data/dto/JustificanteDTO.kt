package com.nxtime.app.data.dto

/**
 * El justificante de una ausencia, sin los bytes (ADR 062). Es un dato de
 * salud: el servidor solo devuelve los que quien pregunta puede ver.
 */
data class JustificanteDTO(
    val peticionId: Long,
    val nombre: String,
    val mime: String,
    val tamanoBytes: Long,
    val subidoEn: String? = null
)

/**
 * Si la empresa deja adjuntarlos y los de las ausencias pedidas. Con la empresa
 * apagada, los que ya había se siguen viendo, pero no se suben más.
 */
data class JustificantesDTO(
    val activo: Boolean = false,
    val justificantes: List<JustificanteDTO> = emptyList()
)
