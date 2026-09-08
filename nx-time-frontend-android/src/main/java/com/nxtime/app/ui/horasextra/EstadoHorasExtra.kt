package com.nxtime.app.ui.horasextra

import androidx.annotation.StringRes
import com.nxtime.app.R

/**
 * En qué punto está un aviso de horas extra, visto desde la app.
 *
 * Espeja `OvertimeStatus.java`. [de] devuelve `null` para un valor que
 * esta versión no conozca, y quien llame debe pintarlo como "otro
 * estado" en vez de inventarse uno — mismo criterio que `Rol.de` y
 * `EstadoCorreccion.de`.
 */
enum class EstadoHorasExtra(@param:StringRes val etiqueta: Int) {

    /** Detectado y sin revisar. Todavía no cuenta como horas extra. */
    ABIERTO(R.string.horas_extra_estado_abierto),

    /** Revisado y descartado: no eran horas extra. No consume bolsa. */
    JUSTIFICADO(R.string.horas_extra_estado_justificado),

    /** Revisado y reconocido. Es lo único que consume la bolsa anual. */
    ACEPTADO(R.string.horas_extra_estado_aceptado);

    /** Si todavía espera a que alguien decida. */
    val esperaDecision: Boolean
        get() = this == ABIERTO

    companion object {
        fun de(valor: String?): EstadoHorasExtra? = entries.firstOrNull { it.name == valor }
    }
}

/**
 * De qué periodo habla un aviso.
 *
 * Espeja `OvertimeType.java`. Importa para pintarlo: en un aviso diario
 * la fecha es el día, y en uno semanal es el lunes de la semana — el
 * texto tiene que decir cuál de las dos cosas es o la fecha se lee mal.
 */
enum class TipoHorasExtra(@param:StringRes val etiqueta: Int) {
    DIARIA(R.string.horas_extra_tipo_diaria),
    SEMANAL(R.string.horas_extra_tipo_semanal);

    companion object {
        fun de(valor: String?): TipoHorasExtra? = entries.firstOrNull { it.name == valor }
    }
}
