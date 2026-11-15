package com.nxtime.app.data.dto

/**
 * Cómo le llega a esta persona una categoría de avisos (ADR 059;
 * GET/PUT /api/v1/perfil/avisos). La categoría va como texto: una nueva no
 * puede romper una versión vieja de la app.
 *
 * @param configurable falso en las obligatorias, que llegan siempre.
 */
data class PreferenciaDeAvisoDTO(
    val categoria: String = "",
    val etiqueta: String = "",
    val descripcion: String? = null,
    val correo: Boolean = true,
    val push: Boolean = true,
    val configurable: Boolean = true
)

data class CambioDePreferenciaDTO(
    val categoria: String,
    val correo: Boolean,
    val push: Boolean
)

data class GuardarPreferenciasRequest(
    val preferencias: List<CambioDePreferenciaDTO>
)
