package com.nxtime.app.ui.proyectos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nxtime.app.R
import com.nxtime.app.data.dto.AsignacionProyectoDTO
import com.nxtime.app.data.dto.EmpleadoSimpleDTO
import com.nxtime.app.ui.components.CampoFecha
import com.nxtime.app.ui.util.DateFormats
import java.time.LocalDate

/**
 * Alta de un proyecto.
 *
 * No pide fecha de fin: casi ningún proyecto la tiene el día que se
 * crea, y ofrecer un campo que casi siempre se deja vacío invita a
 * rellenarlo con una fecha inventada. Se pone luego, editándolo.
 */
@Composable
fun DialogoNuevoProyecto(
    onConfirma: (String, String, String, LocalDate) -> Unit,
    onCancela: () -> Unit
) {
    var codigo by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var desde by remember { mutableStateOf(LocalDate.now(DateFormats.ZONA_ESPANA)) }

    AlertDialog(
        onDismissRequest = onCancela,
        title = { Text(stringResource(R.string.proyectos_nuevo)) },
        text = {
            // Desplazable: con el teclado abierto y la fuente ampliada,
            // el campo de fecha se queda fuera de la pantalla.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it },
                    label = { Text(stringResource(R.string.proyectos_codigo)) },
                    supportingText = { Text(stringResource(R.string.proyectos_codigo_ayuda)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(stringResource(R.string.proyectos_nombre)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text(stringResource(R.string.proyectos_descripcion)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                CampoFecha(
                    etiqueta = stringResource(R.string.proyectos_fecha_inicio),
                    fecha = desde,
                    onElige = { desde = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = codigo.isNotBlank() && nombre.isNotBlank(),
                onClick = { onConfirma(codigo, nombre, descripcion, desde) }
            ) { Text(stringResource(R.string.proyectos_crear)) }
        },
        dismissButton = {
            TextButton(onClick = onCancela) { Text(stringResource(R.string.cancelar)) }
        }
    )
}

/**
 * Asignar a alguien al proyecto.
 *
 * La lista solo trae a quien NO está ya dentro; si alguien está en otro
 * proyecto esas fechas, el servidor lo rechaza con un 409 que dice en
 * cuál — eso no se puede saber aquí sin pedir todas las asignaciones de
 * todo el mundo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoAsignar(
    candidatos: List<EmpleadoSimpleDTO>,
    onConfirma: (Long, LocalDate) -> Unit,
    onCancela: () -> Unit
) {
    var elegido by remember { mutableStateOf(candidatos.firstOrNull()) }
    var desde by remember { mutableStateOf(LocalDate.now(DateFormats.ZONA_ESPANA)) }
    var abierto by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancela,
        title = { Text(stringResource(R.string.proyectos_asignar)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ExposedDropdownMenuBox(
                    expanded = abierto,
                    onExpandedChange = { abierto = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = elegido?.nombre.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.proyectos_empleado)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                        candidatos.forEach { candidato ->
                            DropdownMenuItem(
                                text = { Text(candidato.nombre) },
                                onClick = {
                                    elegido = candidato
                                    abierto = false
                                }
                            )
                        }
                    }
                }
                CampoFecha(
                    etiqueta = stringResource(R.string.proyectos_desde_etiqueta),
                    fecha = desde,
                    onElige = { desde = it }
                )
                Text(
                    text = stringResource(R.string.proyectos_asignar_ayuda),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = elegido != null,
                onClick = { elegido?.let { onConfirma(it.id, desde) } }
            ) { Text(stringResource(R.string.proyectos_asignar_confirmar)) }
        },
        dismissButton = {
            TextButton(onClick = onCancela) { Text(stringResource(R.string.cancelar)) }
        }
    )
}

/**
 * Sacar a alguien del proyecto.
 *
 * El texto dice explícitamente que sus horas se quedan: es la duda
 * inmediata al ver un botón que suena a borrar, y la respuesta es que no
 * borra nada.
 */
@Composable
fun DialogoFinalizar(
    asignacion: AsignacionProyectoDTO,
    onConfirma: (LocalDate) -> Unit,
    onCancela: () -> Unit
) {
    var hasta by remember { mutableStateOf(LocalDate.now(DateFormats.ZONA_ESPANA)) }

    AlertDialog(
        onDismissRequest = onCancela,
        title = { Text(stringResource(R.string.proyectos_sacar)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.proyectos_sacar_aviso, asignacion.usuario),
                    style = MaterialTheme.typography.bodyMedium
                )
                CampoFecha(
                    etiqueta = stringResource(R.string.proyectos_hasta_etiqueta),
                    fecha = hasta,
                    onElige = { hasta = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirma(hasta) }) {
                Text(stringResource(R.string.proyectos_sacar_confirmar))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancela) { Text(stringResource(R.string.cancelar)) }
        }
    )
}
