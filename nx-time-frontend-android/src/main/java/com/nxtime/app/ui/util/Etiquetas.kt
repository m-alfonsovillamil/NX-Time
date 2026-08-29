package com.nxtime.app.ui.util

import androidx.annotation.StringRes
import com.nxtime.app.R
import com.nxtime.app.data.dto.EstadoAusencia
import com.nxtime.app.data.dto.TipoAusencia

/**
 * Traduce los enum del contrato a los textos que se leen en pantalla.
 *
 * El `when` es exhaustivo a propósito y sin rama `else`: si el backend
 * añadiera un tipo de ausencia nuevo, esto deja de compilar y hay que
 * darle nombre, en lugar de que aparezca un "OTROS" silencioso o un
 * hueco en blanco.
 */
@get:StringRes
val TipoAusencia.etiqueta: Int
    get() = when (this) {
        TipoAusencia.VACACIONES -> R.string.tipo_vacaciones
        TipoAusencia.ASUNTOS_PROPIOS -> R.string.tipo_asuntos_propios
        TipoAusencia.MATRIMONIO -> R.string.tipo_matrimonio
        TipoAusencia.FALLECIMIENTO_FAMILIAR -> R.string.tipo_fallecimiento_familiar
        TipoAusencia.HOSPITALIZACION_FAMILIAR -> R.string.tipo_hospitalizacion_familiar
        TipoAusencia.LACTANCIA -> R.string.tipo_lactancia
        TipoAusencia.MATERNIDAD_PATERNIDAD -> R.string.tipo_maternidad_paternidad
        TipoAusencia.MEDICO -> R.string.tipo_medico
        TipoAusencia.TRASLADO_DOMICILIO -> R.string.tipo_traslado_domicilio
        TipoAusencia.VIAJE_TRABAJO -> R.string.tipo_viaje_trabajo
        TipoAusencia.OTROS -> R.string.tipo_otros
    }

@get:StringRes
val EstadoAusencia.etiqueta: Int
    get() = when (this) {
        EstadoAusencia.PENDIENTE -> R.string.ausencias_estado_pendiente
        EstadoAusencia.APROBADA -> R.string.ausencias_estado_aprobada
        EstadoAusencia.RECHAZADA -> R.string.ausencias_estado_rechazada
    }
