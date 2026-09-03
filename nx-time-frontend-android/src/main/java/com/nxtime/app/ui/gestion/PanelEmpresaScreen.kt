package com.nxtime.app.ui.gestion

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BannerError
import com.nxtime.app.ui.components.EstadoCargando
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.informes.MIME_EXCEL
import com.nxtime.app.ui.informes.MIME_PDF
import com.nxtime.app.ui.informes.compartirInforme
import com.nxtime.app.ui.informes.guardarEnCache
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Panel de empresa: cómo va el mes, quién está de alta y los informes.
 *
 * @param puedeGestionarEmpleados si se ofrece el alta/baja
 *   (`empleado:gestionar`, RRHH y ADMIN).
 * @param puedeExportar si se ofrecen los informes (`informe:exportar`,
 *   mismos roles). Van separados porque son authorities distintas en el
 *   backend, aunque hoy coincidan los roles que las tienen.
 */
@Composable
fun PanelEmpresaScreen(
    onVolver: () -> Unit,
    puedeGestionarEmpleados: Boolean,
    puedeExportar: Boolean,
    viewModel: PanelEmpresaViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var aDarDeBaja by remember { mutableStateOf<EmpleadoSimpleDTO?>(null) }

    /*
     * Escribir el fichero necesita el Context, que no tiene por qué estar
     * en el ViewModel: este devuelve el cuerpo de la descarga y aquí se
     * guarda y se abre.
     */
    val textoSinVisor = stringResource(R.string.empresa_sin_visor)
    fun abrir(cuerpo: ResponseBody, nombre: String, tipoMime: String) {
        alcance.launch {
            val fichero = guardarEnCache(contexto, cuerpo, nombre)
            try {
                compartirInforme(contexto, fichero, tipoMime)
            } catch (e: ActivityNotFoundException) {
                // Un emulador limpio no trae visor de Excel ni de PDF. El
                // informe ya está descargado, así que se avisa en vez de
                // dejar que la excepción tire la app.
                Toast.makeText(contexto, textoSinVisor, Toast.LENGTH_LONG).show()
            }
        }
    }

    PantallaConBarra(titulo = stringResource(R.string.empresa_titulo), onVolver = onVolver) { modifier ->
        when {
            estado.cargando -> EstadoCargando(modifier)

            estado.panel == null -> EstadoErrorPantalla(
                mensaje = estado.error?.resolver().orEmpty(),
                onReintentar = viewModel::cargar,
                modifier = modifier
            )

            else -> Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // El error aquí es un banner y no una pantalla entera: el
                // panel ya cargó, y un fallo al descargar o al dar de
                // baja no debe borrar los datos que sí están.
                estado.error?.let { mensaje ->
                    BannerError(mensaje = mensaje.resolver(), onReintentar = viewModel::descartarError)
                    Spacer(Modifier.height(12.dp))
                }

                Indicadores(estado)

                if (puedeExportar) {
                    Spacer(Modifier.height(20.dp))
                    SelectorDeMes(estado.mes, viewModel::cambiarMes)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.descargarExcel { c, n -> abrir(c, n, MIME_EXCEL) } },
                        enabled = !estado.descargando,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.empresa_descargar_excel))
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.empresa_plantilla),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))

                estado.empleados.forEach { empleado ->
                    FilaEmpleado(
                        empleado = empleado,
                        puedeGestionar = puedeGestionarEmpleados,
                        puedeExportar = puedeExportar,
                        descargando = estado.descargando,
                        onCambiaEstado = { activo ->
                            if (activo) {
                                viewModel.cambiarEstadoEmpleado(empleado.id, true)
                            } else {
                                // Dar de baja pide confirmación; reactivar
                                // no: deshacer una baja no tiene coste.
                                aDarDeBaja = empleado
                            }
                        },
                        onDescargaPdf = {
                            viewModel.descargarPdf(empleado.id) { c, n -> abrir(c, n, MIME_PDF) }
                        }
                    )
                }
            }
        }
    }

    aDarDeBaja?.let { empleado ->
        AlertDialog(
            onDismissRequest = { aDarDeBaja = null },
            title = { Text(stringResource(R.string.empresa_baja_titulo, empleado.nombre)) },
            text = { Text(stringResource(R.string.empresa_baja_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cambiarEstadoEmpleado(empleado.id, false)
                    aDarDeBaja = null
                }) {
                    Text(stringResource(R.string.empresa_baja_confirmar))
                }
            },
            dismissButton = {
                TextButton(onClick = { aDarDeBaja = null }) {
                    Text(stringResource(R.string.cancelar))
                }
            }
        )
    }
}

@Composable
private fun Indicadores(estado: PanelEmpresaUiState) {
    val panel = estado.panel ?: return

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Indicador(
            etiqueta = stringResource(R.string.empresa_empleados_activos),
            valor = panel.empleadosActivos.toString(),
            modifier = Modifier.weight(1f)
        )
        Indicador(
            etiqueta = stringResource(R.string.empresa_horas_mes),
            valor = DateFormats.minutos(panel.minutosMesEmpresa),
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Indicador(
            etiqueta = stringResource(R.string.empresa_ausencias_pendientes),
            valor = panel.ausenciasPendientes.toString(),
            modifier = Modifier.weight(1f)
        )
        Indicador(
            etiqueta = stringResource(R.string.empresa_incidencias),
            valor = panel.incidenciasAbiertas.toString(),
            // Las incidencias abiertas son trabajo pendiente real, no un
            // dato informativo: cuando las hay, la tarjeta lo dice en
            // rojo para que no pase desapercibida entre las demás.
            alerta = panel.incidenciasAbiertas > 0,
            ayuda = stringResource(R.string.empresa_incidencias_ayuda),
            modifier = Modifier.weight(1f)
        )
    }

    if (panel.horasPorEmpleado.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.empresa_horas_por_empleado),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        val maximo = panel.horasPorEmpleado.maxOf { it.minutos }.coerceAtLeast(1)
        panel.horasPorEmpleado.forEach { fila ->
            BarraDeHoras(
                nombre = fila.nombre,
                minutos = fila.minutos,
                proporcion = fila.minutos.toFloat() / maximo
            )
        }
    }
}

@Composable
private fun Indicador(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    alerta: Boolean = false,
    ayuda: String? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (alerta) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = if (alerta) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(4.dp))
            Text(text = valor, style = MaterialTheme.typography.headlineSmall)
            if (alerta && ayuda != null) {
                Spacer(Modifier.height(4.dp))
                Text(text = ayuda, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Una barra por empleado, proporcional al que más ha trabajado.
 *
 * Se dibuja con una `Card` de anchura variable en vez de traer una
 * librería de gráficas: son cuatro o cinco filas y un solo eje, y una
 * dependencia entera para esto no se paga sola.
 */
@Composable
private fun BarraDeHoras(nombre: String, minutos: Long, proporcion: Float) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = nombre,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = DateFormats.minutos(minutos),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .weight(proporcion.coerceIn(0.02f, 1f))
                    .height(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {}
            // El hueco restante mantiene la escala: sin él, todas las
            // barras ocuparían el ancho completo y no compararían nada.
            val resto = 1f - proporcion.coerceIn(0.02f, 1f)
            if (resto > 0f) Spacer(Modifier.weight(resto))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorDeMes(mes: YearMonth, onCambia: (YearMonth) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val meses = remember { PanelEmpresaViewModel.mesesDisponibles() }

    ExposedDropdownMenuBox(
        expanded = abierto,
        onExpandedChange = { abierto = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = mes.formateado(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.empresa_mes)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            meses.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion.formateado()) },
                    onClick = { onCambia(opcion); abierto = false }
                )
            }
        }
    }
}

@Composable
private fun FilaEmpleado(
    empleado: EmpleadoSimpleDTO,
    puedeGestionar: Boolean,
    puedeExportar: Boolean,
    descargando: Boolean,
    onCambiaEstado: (Boolean) -> Unit,
    onDescargaPdf: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = empleado.nombre, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (empleado.activo) {
                        empleado.email
                    } else {
                        stringResource(R.string.empresa_empleado_inactivo)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (empleado.activo) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            if (puedeExportar) {
                TextButton(onClick = onDescargaPdf, enabled = !descargando) {
                    Text(stringResource(R.string.empresa_descargar_pdf))
                }
            }
            if (puedeGestionar) {
                Switch(checked = empleado.activo, onCheckedChange = onCambiaEstado)
            }
        }
        HorizontalDivider()
    }
}

/** "septiembre de 2026", en español y con la inicial en mayúscula. */
private fun YearMonth.formateado(): String {
    val es = Locale.forLanguageTag("es-ES")
    val texto = format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", es))
    return texto.replaceFirstChar { it.titlecase(es) }
}
