package com.nxtime.app.ui.horasextra

import androidx.annotation.StringRes
import com.nxtime.app.R
import com.nxtime.app.data.dto.HorasExtraDTO
import java.time.LocalDate

/**
 * En qué punto está un aviso de horas extra, visto desde la app.
 *
 * Espeja `OvertimeStatus.java`. [de] devuelve `null` para un valor que
 * esta versión no conozca, y quien llame debe pintarlo como "otro
 * estado" en vez de inventarse uno — mismo criterio que
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

/** Qué decir de cómo se compensan unas horas aceptadas, con la fecha que toque. */
data class TextoDeCompensacion(@param:StringRes val texto: Int, val fecha: String? = null)

/**
 * Cómo se compensan unas aceptadas (ADR 061): se pagan, se descansan antes de
 * una fecha, ya se descansaron, o se pasó la fecha y cuentan en la bolsa. Las
 * aceptadas antes de poder elegir llegan sin compensación y no dicen nada:
 * cuentan como pagadas, como siempre.
 */
fun textoDeCompensacion(aviso: HorasExtraDTO, hoy: LocalDate): TextoDeCompensacion? {
    if (aviso.estado != EstadoHorasExtra.ACEPTADO.name) return null
    return when (aviso.compensacion) {
        "PAGO" -> TextoDeCompensacion(R.string.horas_extra_se_pagan)
        "DESCANSO" -> when {
            aviso.descansoDisfrutadoEn != null ->
                TextoDeCompensacion(R.string.horas_extra_descansadas, aviso.descansoDisfrutadoEn)
            aviso.descansoAntesDe != null && LocalDate.parse(aviso.descansoAntesDe).isBefore(hoy) ->
                TextoDeCompensacion(R.string.horas_extra_caducadas, aviso.descansoAntesDe)
            else -> TextoDeCompensacion(R.string.horas_extra_se_compensan, aviso.descansoAntesDe ?: aviso.fecha)
        }
        else -> null
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
