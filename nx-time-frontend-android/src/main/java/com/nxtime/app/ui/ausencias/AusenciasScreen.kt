package com.nxtime.app.ui.ausencias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.ListaConRecarga
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.components.TarjetaAusencia
import com.nxtime.app.ui.util.resolver

@Composable
fun AusenciasScreen(
    onIrSolicitud: () -> Unit,
    viewModel: AusenciasViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    // Sin flecha de volver: es un destino de la barra de navegación.
    PantallaConBarra(
        titulo = stringResource(R.string.ausencias_titulo),
        accionFlotante = {
            FloatingActionButton(onClick = onIrSolicitud) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.nav_solicitar)
                )
            }
        }
    ) { modifier ->
        ListaConRecarga(
            cargando = estado.cargando,
            hayContenido = estado.peticiones.isNotEmpty(),
            onRecargar = viewModel::cargar,
            modifier = modifier
        ) {
            when {
                estado.error != null -> EstadoErrorPantalla(
                    mensaje = estado.error!!.resolver(),
                    onReintentar = viewModel::cargar
                )

                estado.peticiones.isEmpty() -> EstadoVacio(
                    titulo = stringResource(R.string.ausencias_vacio_titulo),
                    texto = stringResource(R.string.ausencias_vacio_texto)
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(estado.peticiones, key = { it.id }) { peticion ->
                        TarjetaAusencia(peticion = peticion)
                    }
                }
            }
        }
    }
}
