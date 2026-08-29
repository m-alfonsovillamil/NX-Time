package com.nxtime.app.ui.ausencias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.EstadoCargando
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.EstadoVacio
import com.nxtime.app.ui.components.TarjetaAusencia
import com.nxtime.app.ui.util.resolver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AusenciasScreen(
    onVolver: () -> Unit,
    onIrSolicitud: () -> Unit,
    viewModel: AusenciasViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ausencias_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.volver)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onIrSolicitud) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.nav_solicitar)
                )
            }
        }
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)

        when {
            estado.cargando -> EstadoCargando(modifier)

            estado.error != null -> EstadoErrorPantalla(
                mensaje = estado.error!!.resolver(),
                onReintentar = viewModel::cargar,
                modifier = modifier
            )

            estado.peticiones.isEmpty() -> EstadoVacio(
                titulo = stringResource(R.string.ausencias_vacio_titulo),
                texto = stringResource(R.string.ausencias_vacio_texto),
                modifier = modifier
            )

            else -> LazyColumn(
                modifier = modifier,
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
