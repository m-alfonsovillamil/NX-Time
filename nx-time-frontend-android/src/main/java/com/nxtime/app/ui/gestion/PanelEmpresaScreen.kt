package com.nxtime.app.ui.gestion

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.ui.theme.elevacionDeTarjeta
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
import com.nxtime.app.ui.util.MensajeUi
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
    var aConfigurar by remember { mutableStateOf<EmpleadoSimpleDTO?>(null) }

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
                        onConfigurar = { aConfigurar = empleado },
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

    aConfigurar?.let { empleado ->
        DialogoFicha(
            empleado = empleado,
            guardando = estado.guardandoFicha,
            error = estado.errorFicha,
            onCerrar = {
                aConfigurar = null
                viewModel.descartarErrorDeFicha()
            },
            onGuardar = { horas, dias ->
                viewModel.guardarFicha(empleado.id, horas, dias) { aConfigurar = null }
            }
        )
    }
}

/**
 * Jornada semanal y días de vacaciones de un empleado.
 *
 * Un diálogo y no una pantalla propia: son dos campos numéricos, y el
 * panel ya usa este mismo patrón para la baja. Una ruta nueva con su
 * argumento, su `navArgument` y su ViewModel sería más andamiaje que
 * formulario.
 */
@Composable
private fun DialogoFicha(
    empleado: EmpleadoSimpleDTO,
    guardando: Boolean,
    error: MensajeUi?,
    onCerrar: () -> Unit,
    onGuardar: (String, String) -> Unit
) {
    // La clave es el empleado: al abrir la fila de otro, los campos se
    // reinician con SUS valores en vez de arrastrar los del anterior.
    var horas by remember(empleado.id) { mutableStateOf(empleado.horasSemanales) }
    var dias by remember(empleado.id) { mutableStateOf(empleado.diasVacaciones.toString()) }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(stringResource(R.string.empresa_ficha_titulo, empleado.nombre)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = horas,
                    onValueChange = { horas = it },
                    label = { Text(stringResource(R.string.empresa_ficha_horas)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dias,
                    onValueChange = { dias = it },
                    label = { Text(stringResource(R.string.empresa_ficha_dias)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.empresa_ficha_ayuda),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (error != null) {
                    Text(
                        text = error.resolver(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(horas, dias) }, enabled = !guardando) {
                Text(stringResource(R.string.empresa_ficha_guardar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) {
                Text(stringResource(R.string.empresa_ficha_cancelar))
            }
        }
    )
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
        /*
         * La escala se fija con la MEDIA del equipo como referencia, no
         * solo con el máximo. Con barras proporcionales al mayor, quien
         * más ha trabajado siempre llena la barra y todos los demás se
         * ven "cortos": el gráfico dice quién trabaja más, que ya se lee
         * en los números, y no dice lo único que importa -- quién se sale
         * de lo normal.
         *
         * El tope de la escala es el mayor entre el máximo y la media,
         * para que la marca de la media siempre caiga dentro del dibujo.
         */
        val media = panel.horasPorEmpleado.sumOf { it.minutos } / panel.horasPorEmpleado.size
        val maximo = panel.horasPorEmpleado.maxOf { it.minutos }.coerceAtLeast(1)
        val tope = maxOf(maximo, media).coerceAtLeast(1)

        panel.horasPorEmpleado.forEach { fila ->
            BarraDeHoras(
                nombre = fila.nombre,
                minutos = fila.minutos,
                proporcion = fila.minutos.toFloat() / tope,
                proporcionMedia = media.toFloat() / tope,
                porEncimaDeLaMedia = fila.minutos > media
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empresa_media_equipo, DateFormats.minutos(media)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        elevation = elevacionDeTarjeta(),
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
 * Una barra por empleado, con la media del equipo marcada encima.
 *
 * Sin esa marca, la gráfica no decía nada: eran barras planas sin escala
 * ni referencia, así que "9h 30m" quedaba tan suelto como el número que
 * ya estaba escrito al lado. Con la línea de la media se lee de un
 * vistazo lo único que un gestor busca aquí -- **quién se sale de lo
 * normal**, hacia arriba o hacia abajo.
 *
 * Se dibuja con `Box` y anchuras proporcionales en vez de traer una
 * librería de gráficas: son cuatro o cinco filas y un solo eje, y una
 * dependencia entera para esto no se paga sola.
 */
@Composable
private fun BarraDeHoras(
    nombre: String,
    minutos: Long,
    proporcion: Float,
    proporcionMedia: Float,
    porEncimaDeLaMedia: Boolean
) {
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
                // Quien está por encima de la media se marca en el color
                // de gestión; el resto queda en gris.
                color = if (porEncimaDeLaMedia) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            // El carril de fondo da la escala completa: sin él, todas las
            // barras parecerían llenas y no compararían nada.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(proporcion.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
            )
            /*
             * La marca de la media, encima de todo y sobresaliendo de la
             * barra por arriba y por abajo para que se lea como una
             * referencia y no como un trozo de la propia barra.
             *
             * Va en dos capas -- un filo del color de la tarjeta y un
             * nucleo gris dentro -- porque tiene que verse sobre dos
             * fondos distintos: el carril vacio, que es gris claro, y el
             * relleno indigo de quien esta por encima de la media, que es
             * justo la fila donde la marca mas importa. Con una sola capa
             * gris, la marca de Javier Lopez desaparecia dentro de su
             * propia barra.
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth(proporcionMedia.coerceIn(0f, 1f))
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
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
    onConfigurar: () -> Unit,
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
                // La ficha se enseña siempre, aunque no se pueda editar:
                // saber la jornada de alguien es parte de llevar un
                // equipo, cambiarla es cosa de RRHH.
                Text(
                    text = stringResource(
                        R.string.empresa_ficha_resumen,
                        empleado.horasSemanales,
                        empleado.diasVacaciones
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (puedeGestionar) {
                IconButton(onClick = onConfigurar) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = stringResource(
                            R.string.empresa_ficha_editar, empleado.nombre
                        )
                    )
                }
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
