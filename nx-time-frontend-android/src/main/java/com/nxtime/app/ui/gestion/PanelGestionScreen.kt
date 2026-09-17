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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkOutline
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
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.R
import com.nxtime.app.ui.components.Avatar
import com.nxtime.app.ui.components.CampanaDeAvisos
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
    contadorAvisos: Int,
    onIrAvisos: () -> Unit,
    iniciales: String,
    onIrPerfil: () -> Unit,
    puedeCrearGestores: Boolean,
    puedeVerPanelEmpresa: Boolean,
    onIrPanelEmpresa: () -> Unit,
    onIrProyectos: () -> Unit,
    onIrCorrecciones: () -> Unit,
    onIrHorasExtra: () -> Unit,
    puedeInstruirDenuncias: Boolean,
    onIrCanalDenuncias: () -> Unit,
    puedePublicarOfertas: Boolean,
    onIrGestionOfertas: () -> Unit,
    onIrHistorialEquipo: () -> Unit,
    onIrPendientes: () -> Unit,
    onIrResueltas: () -> Unit,
    onIrAltaEmpleado: () -> Unit,
    onIrAltaGestor: () -> Unit
) {
    // Sin flecha de volver: es un destino de la barra de navegación.
    PantallaConBarra(
        titulo = stringResource(R.string.gestion_titulo),
        acciones = {
            CampanaDeAvisos(contadorAvisos, onIrAvisos)
            Avatar(
                iniciales = iniciales,
                descripcion = stringResource(R.string.perfil_abrir),
                onClick = onIrPerfil,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            /*
             * PENDIENTE DE RESOLVER: las tres bandejas. Van primero
             * porque son lo único de esta pantalla que espera una
             * decisión, y es a lo que un gestor entra cada mañana.
             *
             * Ninguna se gatea aquí. Las correcciones y las horas extra
             * las ve cualquier rol de gestión, y lo que puede RESOLVER
             * cada uno lo decide el servidor: sobre los avisos propios no
             * puede decidir ninguno, y el servicio ni siquiera los manda
             * en esa lista.
             */
            CabeceraDeSeccion(stringResource(R.string.gestion_seccion_pendiente))
            OpcionGestion(
                texto = stringResource(R.string.gestion_ausencias_pendientes),
                icono = Icons.Default.PendingActions,
                onClick = onIrPendientes
            )
            OpcionGestion(
                texto = stringResource(R.string.correcciones_titulo),
                icono = Icons.Default.EditNote,
                onClick = onIrCorrecciones
            )
            OpcionGestion(
                texto = stringResource(R.string.gestion_horas_extra),
                icono = Icons.Default.MoreTime,
                onClick = onIrHorasExtra
            )

            /*
             * EQUIPO: las personas y su historial. Aquí se juntan las dos
             * parejas que antes estaban separadas por media lista:
             * ausencias pendientes/resueltas --que son la misma pantalla
             * con un booleano-- y el alta de empleado/gestor, que son el
             * mismo formulario.
             *
             * Las resueltas van con el equipo y no con las pendientes: no
             * esperan nada, se consultan.
             */
            CabeceraDeSeccion(stringResource(R.string.gestion_seccion_equipo))
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

            /*
             * EMPRESA: la vista de conjunto y los catálogos.
             *
             * Los proyectos los ve cualquier rol de gestión; lo que hay
             * DENTRO (alta y asignaciones) se gatea aparte, dentro de la
             * propia pantalla, con `proyecto:gestionar`.
             *
             * El canal de denuncias NO sigue esa regla: aquí sí se gatea,
             * y solo lo ve un ADMIN. La denuncia puede ser sobre el GESTOR
             * que está mirando esta misma pantalla, así que ni siquiera la
             * entrada debe aparecerle -- saber que el canal tiene
             * expedientes ya es información.
             */
            CabeceraDeSeccion(stringResource(R.string.gestion_seccion_empresa))
            if (puedeVerPanelEmpresa) {
                OpcionGestion(
                    texto = stringResource(R.string.empresa_titulo),
                    icono = Icons.Default.Insights,
                    onClick = onIrPanelEmpresa
                )
            }
            OpcionGestion(
                texto = stringResource(R.string.proyectos_titulo),
                icono = Icons.Default.WorkOutline,
                onClick = onIrProyectos
            )
            if (puedePublicarOfertas) {
                OpcionGestion(
                    texto = stringResource(R.string.gestion_ofertas),
                    icono = Icons.Default.Campaign,
                    onClick = onIrGestionOfertas
                )
            }
            if (puedeInstruirDenuncias) {
                OpcionGestion(
                    texto = stringResource(R.string.gestion_canal_denuncias),
                    icono = Icons.Default.Shield,
                    onClick = onIrCanalDenuncias
                )
            }
        }
    }
}

/**
 * El rótulo que separa un bloque del siguiente.
 *
 * Texto pequeño y en `onSurfaceVariant`, no otra tarjeta: una cabecera con
 * el mismo peso visual que las opciones se leería como una opción más que
 * no hace nada al tocarla.
 *
 * ⚠️ Las tres secciones tienen al menos una entrada que ven TODOS los roles
 * de gestión (ausencias pendientes, historial del equipo y proyectos), así
 * que ninguna cabecera puede quedarse suelta sobre un bloque vacío. Si
 * alguna de esas tres pasa a gatearse, hay que volver por aquí.
 */
@Composable
private fun CabeceraDeSeccion(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp)
    )
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
        elevation = elevacionDeTarjeta(),
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
                // Índigo, no el teal de siempre: es la señal de que has
                // entrado en la zona de gestión. El resto de la app usa el
                // primario, así que el cambio de familia de color se lee
                // sin tener que anunciarlo.
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.size(16.dp))
            Text(text = texto, style = MaterialTheme.typography.titleMedium)
        }
    }
}
