package com.nxtime.app.ui.gestion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nxtime.app.R
import com.nxtime.app.ui.components.PantallaConBarra

/**
 * Panel de gestión: la puerta a las pantallas de gestor.
 *
 * No tiene ViewModel porque no tiene estado ni pide datos; es un menú.
 * La versión anterior (`ManagerHomeActivity`) era también eso, pero
 * apilaba botones a pantalla completa sin decir a dónde llevaba cada
 * uno más allá de su texto.
 *
 * @param puedeCrearGestores si se enseña el alta de gestores. Es el
 *   único punto del panel que no vale para todos los roles de gestión:
 *   `gestor:crear` la tiene solo ADMIN, y hasta ahora la opción se le
 *   ofrecía también a un GESTOR, para el que el backend respondía 403
 *   sin excepción.
 */
@Composable
fun PanelGestionScreen(
    puedeCrearGestores: Boolean,
    puedeVerPanelEmpresa: Boolean,
    onIrPanelEmpresa: () -> Unit,
    onIrHistorialEquipo: () -> Unit,
    onIrPendientes: () -> Unit,
    onIrResueltas: () -> Unit,
    onIrAltaEmpleado: () -> Unit,
    onIrAltaGestor: () -> Unit
) {
    // Sin flecha de volver: es un destino de la barra de navegación.
    PantallaConBarra(
        titulo = stringResource(R.string.gestion_titulo)
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Primero el panel: es la vista de conjunto desde la que se
            // decide a qué mirar después.
            if (puedeVerPanelEmpresa) {
                OpcionGestion(
                    texto = stringResource(R.string.empresa_titulo),
                    icono = Icons.Default.Insights,
                    onClick = onIrPanelEmpresa
                )
            }
            OpcionGestion(
                texto = stringResource(R.string.gestion_ausencias_pendientes),
                icono = Icons.Default.PendingActions,
                onClick = onIrPendientes
            )
            OpcionGestion(
                texto = stringResource(R.string.gestion_historial_equipo),
                icono = Icons.AutoMirrored.Filled.ListAlt,
                onClick = onIrHistorialEquipo
            )
            OpcionGestion(
                texto = stringResource(R.string.gestion_ausencias_resueltas),
                icono = Icons.Default.EventAvailable,
                onClick = onIrResueltas
            )
            OpcionGestion(
                texto = stringResource(R.string.gestion_crear_empleado),
                icono = Icons.Default.PersonAdd,
                onClick = onIrAltaEmpleado
            )
            if (puedeCrearGestores) {
                OpcionGestion(
                    texto = stringResource(R.string.gestion_crear_gestor),
                    icono = Icons.Default.HowToReg,
                    onClick = onIrAltaGestor
                )
            }
        }
    }
}

@Composable
private fun OpcionGestion(
    texto: String,
    icono: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(16.dp))
            Text(text = texto, style = MaterialTheme.typography.titleMedium)
        }
    }
}
