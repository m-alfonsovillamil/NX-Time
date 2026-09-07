package com.nxtime.app.ui.correcciones

import androidx.annotation.StringRes
import com.nxtime.app.R

/**
 * En qué punto está una solicitud de corrección, visto desde la app.
 *
 * Espeja `CorrectionStatus.java`. [de] devuelve `null` para un valor que
 * esta versión no conozca, y quien llame debe pintarlo como "otro
 * estado" en vez de inventarse uno — mismo criterio que `Rol.de`.
 */
enum class EstadoCorreccion(@param:StringRes val etiqueta: Int) {
    PENDIENTE(R.string.correcciones_estado_pendiente),
    APROBADA(R.string.correcciones_estado_aprobada),
    RECHAZADA(R.string.correcciones_estado_rechazada),

    /**
     * El dueño del fichaje no acepta la corrección. No es un rechazo: un
     * rechazo la cierra, y esto la escala a Recursos Humanos.
     */
    EN_DISPUTA(R.string.correcciones_estado_en_disputa);

    /** Si sigue esperando a alguien. */
    val estaViva: Boolean
        get() = this == PENDIENTE || this == EN_DISPUTA

    companion object {
        fun de(valor: String?): EstadoCorreccion? = entries.firstOrNull { it.name == valor }
    }
}
