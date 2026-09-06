package com.nxtime.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nxtime.app.R
import com.nxtime.app.ui.util.DateFormats

/**
 * La hora de salida de una jornada, diciendo si es de otro día.
 *
 * Las tres tarjetas de jornada (historial propio, historial del equipo y
 * el formulario de corrección) pintaban `DateFormats.hora(salida)` a
 * secas, y con el turno de noche eso miente: una jornada de 22:52 a
 * 00:29 se leía como "Entrada 22:52 h / Salida 00:29 h", que parece ir
 * hacia atrás en el tiempo. Ahora sale "00:29 h (+1 d)".
 *
 * Es una función y no un `Text` a propósito: las tres pantallas la
 * meten dentro de componentes distintos (`Dato`, `DatoEquipo`, una fila
 * del formulario) que ya deciden tipografía y color, así que lo único
 * que se comparte es el texto.
 */
@Composable
fun horaDeSalida(entradaIso: String?, salidaIso: String?): String {
    if (salidaIso == null) {
        return stringResource(R.string.historial_en_curso)
    }
    val hora = DateFormats.hora(salidaIso)
    val dias = DateFormats.diasDeDiferencia(entradaIso, salidaIso)
    return if (dias > 0) stringResource(R.string.historial_salida_otro_dia, hora, dias) else hora
}
