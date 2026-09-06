package com.nxtime.app.ui.calendario

import androidx.annotation.StringRes
import com.nxtime.app.R

/**
 * De dónde viene un festivo, visto desde la app.
 *
 * Espeja `HolidayScope.java`. La traducción de la cadena que manda el
 * backend pasa por [de], que devuelve `null` para un valor desconocido:
 * quien llame debe tratarlo como "festivo, sin más detalle", nunca
 * inventarse uno. Es el mismo criterio que `Rol.de` y `AccionAuditoria`.
 *
 * @param etiqueta cómo se llama en pantalla.
 * @param sePuedeElegir si un gestor puede dar de alta festivos con este
 *   ámbito. NACIONAL no: esos son una fila compartida por todas las
 *   empresas y los pone el sistema, así que ni siquiera se ofrece en el
 *   desplegable -- el servidor devolvería un 400.
 */
enum class AmbitoFestivo(
    @param:StringRes val etiqueta: Int,
    val sePuedeElegir: Boolean
) {
    NACIONAL(R.string.calendario_ambito_nacional, sePuedeElegir = false),
    AUTONOMICO(R.string.calendario_ambito_autonomico, sePuedeElegir = true),
    LOCAL(R.string.calendario_ambito_local, sePuedeElegir = true),
    EMPRESA(R.string.calendario_ambito_empresa, sePuedeElegir = true);

    companion object {
        fun de(valor: String?): AmbitoFestivo? = entries.firstOrNull { it.name == valor }

        /** Los que se pueden dar de alta, en el orden del desplegable. */
        val ELEGIBLES: List<AmbitoFestivo> = entries.filter { it.sePuedeElegir }
    }
}
