package com.nxtime.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nxtime.app.R
import com.nxtime.app.data.dto.EstadoUbicacion
import com.nxtime.app.data.dto.UbicacionDelFichaje

/**
 * Dónde se fichó (ADR 056): «Entrada · ± 25 m · Ver en el mapa», o «Sin
 * ubicación» si la empresa la pedía y no se pudo tomar. Sin nada que decir, no
 * pinta nada: la empresa no la pide, o ya se borraron las coordenadas.
 *
 * El mapa se abre fuera, en OpenStreetMap: las coordenadas solo salen de la app
 * si alguien pulsa.
 */
@Composable
fun UbicacionDeFichaje(ubicacion: UbicacionDelFichaje?, momento: String, modifier: Modifier = Modifier) {
    if (ubicacion == null) return
    if (ubicacion.estado != EstadoUbicacion.CAPTURADA) {
        Insignia(
            texto = stringResource(R.string.ubicacion_sin_de, momento),
            modifier = modifier
        )
        return
    }
    val latitud = ubicacion.latitud ?: return
    val longitud = ubicacion.longitud ?: return
    val abrir = LocalUriHandler.current
    val descripcion = stringResource(R.string.ubicacion_ver_en_mapa_de, momento)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = ubicacion.precisionM?.let { stringResource(R.string.ubicacion_precision, momento, it) } ?: momento,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { abrir.openUri(enlaceAlMapa(latitud, longitud)) },
            modifier = Modifier.semantics { contentDescription = descripcion }
        ) {
            Text(stringResource(R.string.ubicacion_ver_en_mapa))
        }
    }
}

/** Con punto decimal, sea cual sea el idioma del móvil: es lo que pinta `Double.toString`. */
fun enlaceAlMapa(latitud: Double, longitud: Double): String =
    "https://www.openstreetmap.org/?mlat=$latitud&mlon=$longitud#map=17/$latitud/$longitud"
