package com.nxtime.app.ui.cuadrante

import androidx.annotation.StringRes
import com.nxtime.app.R

/**
 * En qué punto está una incidencia de cuadrante. Espeja
 * `ScheduleIncidentStatus.java`; [de] devuelve `null` para un valor que esta
 * versión no conozca, mismo criterio que `EstadoHorasExtra.de`.
 */
enum class EstadoIncidencia(@param:StringRes val etiqueta: Int) {

    /** Detectada y sin explicar. */
    PENDIENTE(R.string.incidencias_estado_pendiente),

    /** Explicada por quien la tiene; nadie ha decidido todavía. */
    JUSTIFICADA(R.string.incidencias_estado_justificada),

    ACEPTADA(R.string.incidencias_estado_aceptada),
    RECHAZADA(R.string.incidencias_estado_rechazada);

    /**
     * Si todavía espera a que alguien decida. Una PENDIENTE también: quien
     * revisa puede aceptarla sin esperar a que la expliquen.
     */
    val esperaDecision: Boolean
        get() = this == PENDIENTE || this == JUSTIFICADA

    companion object {
        fun de(valor: String?): EstadoIncidencia? = entries.firstOrNull { it.name == valor }
    }
}

/** Espeja `ScheduleIncidentType.java`. */
enum class TipoIncidencia(@param:StringRes val etiqueta: Int) {
    RETRASO(R.string.incidencias_tipo_retraso),
    SALIDA_ANTICIPADA(R.string.incidencias_tipo_salida),
    AUSENCIA(R.string.incidencias_tipo_ausencia);

    companion object {
        fun de(valor: String?): TipoIncidencia? = entries.firstOrNull { it.name == valor }
    }
}
