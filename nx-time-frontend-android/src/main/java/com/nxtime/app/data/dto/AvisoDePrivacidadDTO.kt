package com.nxtime.app.data.dto

/**
 * Cuerpo de `GET /api/v1/perfil/aviso-de-privacidad` (ADR 047): la empresa,
 * que es la responsable de los datos, a quién dirigirse (un correo o un
 * enlace https; null si el ADMIN no lo ha puesto) y si ya se vio.
 */
data class AvisoDePrivacidadDTO(
    val empresa: String? = null,
    val contacto: String? = null,
    val visto: Boolean = false
)
