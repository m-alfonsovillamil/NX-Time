package com.nxtime.app.ui.ofertas

import androidx.annotation.StringRes
import com.nxtime.app.R

/**
 * En qué punto está una oferta interna, visto desde la app (Fase H).
 *
 * Espeja `JobPostingStatus.java`. [de] devuelve `null` para un valor que
 * esta versión no conozca — mismo criterio que `Rol.de` y
 * `EstadoDenuncia.de`.
 *
 * **`ABIERTA` no significa que admita candidaturas.** Eso depende
 * además de la fecha de cierre, y llega resuelto desde el servidor en
 * `admiteCandidaturas`: la app no lo calcula, porque una regla en dos
 * sitios acaba diciendo dos cosas.
 */
enum class EstadoOferta(@param:StringRes val etiqueta: Int) {

    /** Escrita y sin publicar. Solo la ve quien puede publicar. */
    BORRADOR(R.string.oferta_estado_borrador),

    /** Publicada. La ve toda la plantilla. */
    ABIERTA(R.string.oferta_estado_abierta),

    /** Cerrada. No admite candidaturas, y las que hay siguen ahí. */
    CERRADA(R.string.oferta_estado_cerrada);

    companion object {
        fun de(valor: String?): EstadoOferta? = entries.firstOrNull { it.name == valor }
    }
}

/**
 * En qué punto está una candidatura.
 *
 * Espeja `ApplicationStatus.java`. [esFinal] es lo que decide si la
 * pantalla sigue ofreciendo botones: una candidatura resuelta no se
 * vuelve a mover, y el servidor lo rechaza con un 409.
 */
enum class EstadoCandidatura(@param:StringRes val etiqueta: Int) {

    RECIBIDA(R.string.candidatura_estado_recibida),
    EN_PROCESO(R.string.candidatura_estado_en_proceso),
    DESCARTADA(R.string.candidatura_estado_descartada),
    SELECCIONADA(R.string.candidatura_estado_seleccionada);

    /** Si ya cerró, en un sentido o en el otro. */
    val esFinal: Boolean
        get() = this == DESCARTADA || this == SELECCIONADA

    companion object {
        fun de(valor: String?): EstadoCandidatura? = entries.firstOrNull { it.name == valor }
    }
}
