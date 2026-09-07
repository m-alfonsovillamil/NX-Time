package com.nxtime.app.ui.perfil

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.nxtime.app.data.dto.AdjuntoDTO
import com.nxtime.app.ui.informes.compartirInforme
import com.nxtime.app.ui.informes.guardarEnCache
import com.nxtime.app.ui.util.MensajeUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nxtime.app.R
import com.nxtime.app.data.dto.PerfilDTO
import com.nxtime.app.ui.AppViewModelProvider
import com.nxtime.app.ui.components.BotonPrincipal
import com.nxtime.app.ui.components.EstadoCargando
import com.nxtime.app.ui.components.EstadoErrorPantalla
import com.nxtime.app.ui.components.PantallaConBarra
import com.nxtime.app.ui.theme.elevacionDeTarjeta
import com.nxtime.app.ui.util.DateFormats
import com.nxtime.app.ui.util.resolver

/**
 * Mi perfil.
 *
 * **Absorbe el menú de tres puntos de "Mi jornada"**: cambiar la
 * contraseña y cerrar sesión son cosas de la cuenta, y su sitio natural
 * es donde está la cuenta, no un desbordamiento en la pantalla de
 * fichar. Por eso `FicharScreen` se queda ya sin ese menú.
 *
 * Lo que se ve pero no se edita — rol, jornada, vacaciones,
 * departamento — se pinta aparte y sin lápiz: el backend no lo acepta
 * en `PATCH /perfil`, y ofrecer un campo para comerse un 403 sería
 * peor que no ofrecerlo.
 */
@Composable
fun PerfilScreen(
    onVolver: () -> Unit,
    onIrContrasena: () -> Unit,
    onCerrarSesion: () -> Unit,
    viewModel: PerfilViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    /*
     * Leer el Uri elegido necesita el ContentResolver, que vive en el
     * Context: por eso los bytes se leen aquí y el ViewModel recibe un
     * ByteArray. Es el mismo reparto que en la descarga de informes,
     * donde el ViewModel devuelve el cuerpo y la pantalla escribe.
     */
    fun leerYSubir(uri: Uri?, tipo: String) {
        if (uri == null) return
        alcance.launch {
            val leido = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = contexto.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val nombre = nombreDelUri(contexto, uri)
                    val mime = contexto.contentResolver.getType(uri) ?: "application/octet-stream"
                    Triple(bytes, nombre, mime)
                }.getOrNull()
            }
            val bytes = leido?.first
            if (bytes == null) {
                Toast.makeText(contexto, R.string.perfil_adjunto_ilegible, Toast.LENGTH_LONG).show()
                return@launch
            }
            viewModel.subirAdjunto(bytes, leido.second, leido.third, tipo)
        }
    }

    val elegirCv = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        leerYSubir(uri, TIPO_CV)
    }
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        leerYSubir(uri, TIPO_FOTO)
    }

    val textoSinVisor = stringResource(R.string.empresa_sin_visor)
    fun abrirCv(cuerpo: ResponseBody, nombre: String) {
        alcance.launch {
            val fichero = guardarEnCache(contexto, cuerpo, nombre, subcarpeta = "adjuntos")
            try {
                compartirInforme(contexto, fichero, "application/pdf")
            } catch (e: ActivityNotFoundException) {
                // Un emulador limpio no trae visor de PDF: el fichero ya
                // está descargado, así que se avisa en vez de dejar que
                // la excepción tire la app.
                Toast.makeText(contexto, textoSinVisor, Toast.LENGTH_LONG).show()
            }
        }
    }

    PantallaConBarra(titulo = stringResource(R.string.perfil_titulo), onVolver = onVolver) { modifier ->
        when {
            estado.cargando -> EstadoCargando(modifier)

            estado.perfil == null -> EstadoErrorPantalla(
                mensaje = estado.error?.resolver().orEmpty(),
                onReintentar = viewModel::cargar,
                modifier = modifier
            )

            else -> {
                val perfil = estado.perfil!!
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Cabecera(perfil, estado.foto, onCambiarFoto = { elegirFoto.launch("image/*") })
                    Spacer(Modifier.height(24.dp))

                    if (estado.editando) {
                        Formulario(estado, viewModel)
                    } else {
                        DatosPersonales(perfil, onEditar = viewModel::empezarAEditar)
                    }

                    Spacer(Modifier.height(24.dp))
                    MiCurriculum(
                        cv = estado.cv,
                        subiendo = estado.subiendo,
                        descargando = estado.descargandoCv,
                        error = estado.errorFormulario.takeIf { !estado.editando },
                        onElegir = { elegirCv.launch("application/pdf") },
                        onDescargar = { viewModel.descargarCv(::abrirCv) },
                        onBorrar = viewModel::borrarCv
                    )

                    Spacer(Modifier.height(24.dp))
                    DatosLaborales(perfil)

                    Spacer(Modifier.height(24.dp))
                    Cuenta(onIrContrasena = onIrContrasena, onCerrarSesion = onCerrarSesion)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Cabecera(perfil: PerfilDTO, foto: ImageBitmap?, onCambiarFoto: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier
                .size(72.dp)
                .clickable(onClick = onCambiarFoto),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (foto != null) {
                    Image(
                        bitmap = foto,
                        contentDescription = stringResource(R.string.perfil_foto_cambiar),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Las iniciales son el respaldo, no un hueco: el
                    // perfil se lee igual de bien sin foto.
                    Text(
                        text = perfil.iniciales,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Spacer(Modifier.size(16.dp))
        Column {
            Text(perfil.nombreCompleto, style = MaterialTheme.typography.titleLarge)
            Text(
                text = perfil.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onCambiarFoto, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.perfil_foto_cambiar))
            }
        }
    }
}

@Composable
private fun DatosPersonales(perfil: PerfilDTO, onEditar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.perfil_datos_personales),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onEditar) { Text(stringResource(R.string.perfil_editar)) }
            }
            Spacer(Modifier.height(8.dp))
            Dato(stringResource(R.string.perfil_apellidos), perfil.apellidos)
            Dato(
                stringResource(R.string.perfil_fecha_nacimiento),
                perfil.fechaNacimiento?.let { DateFormats.fechaCorta(it) }
            )
            Dato(stringResource(R.string.perfil_puesto), perfil.puesto)
        }
    }
}

@Composable
private fun DatosLaborales(perfil: PerfilDTO) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.perfil_datos_laborales),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Dato(stringResource(R.string.perfil_departamento), perfil.departamentoNombre)
            Dato(stringResource(R.string.perfil_rol), perfil.rol)
            Dato(
                stringResource(R.string.perfil_jornada),
                stringResource(R.string.perfil_jornada_valor, perfil.horasSemanales)
            )
            Dato(
                stringResource(R.string.perfil_vacaciones),
                stringResource(R.string.perfil_vacaciones_valor, perfil.diasVacaciones)
            )
            Spacer(Modifier.height(8.dp))
            // Decirlo en la pantalla evita que alguien busque un lápiz
            // que no existe.
            Text(
                text = stringResource(R.string.perfil_laborales_solo_rrhh),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Cuenta(onIrContrasena: () -> Unit, onCerrarSesion: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.perfil_cuenta), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onIrContrasena, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nav_contrasena))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nav_cerrar_sesion))
            }
        }
    }
}

@Composable
private fun Formulario(estado: PerfilUiState, viewModel: PerfilViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.perfil_datos_personales),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = estado.nombre,
                onValueChange = viewModel::onNombreCambia,
                label = { Text(stringResource(R.string.perfil_nombre)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = estado.apellidos,
                onValueChange = viewModel::onApellidosCambia,
                label = { Text(stringResource(R.string.perfil_apellidos)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = estado.fechaNacimiento,
                onValueChange = viewModel::onFechaNacimientoCambia,
                label = { Text(stringResource(R.string.perfil_fecha_nacimiento)) },
                placeholder = { Text(stringResource(R.string.perfil_fecha_formato)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = estado.puesto,
                onValueChange = viewModel::onPuestoCambia,
                label = { Text(stringResource(R.string.perfil_puesto)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            estado.errorFormulario?.let { mensaje ->
                Text(
                    text = mensaje.resolver(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            BotonPrincipal(
                texto = stringResource(R.string.perfil_guardar),
                onClick = viewModel::guardar,
                cargando = estado.guardando
            )
            TextButton(
                onClick = viewModel::cancelarEdicion,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cancelar))
            }
        }
    }
}

/** Una fila "etiqueta / valor", con un guion cuando el dato no está. */
@Composable
private fun Dato(etiqueta: String, valor: String?) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (valor.isNullOrBlank()) stringResource(R.string.perfil_sin_dato) else valor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
    HorizontalDivider()
}

/**
 * Mi currículum: subirlo, descargarlo y borrarlo.
 *
 * El formato aceptado se dice en pantalla, y no por cortesía: el
 * servidor comprueba el CONTENIDO del fichero, no su extensión, así que
 * un .doc renombrado a .pdf se rechaza y sin este aviso el mensaje de
 * error parecería arbitrario.
 */
@Composable
private fun MiCurriculum(
    cv: AdjuntoDTO?,
    subiendo: Boolean,
    descargando: Boolean,
    error: MensajeUi?,
    onElegir: () -> Unit,
    onDescargar: () -> Unit,
    onBorrar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = elevacionDeTarjeta(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.perfil_cv), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (cv == null) {
                Text(
                    text = stringResource(R.string.perfil_cv_vacio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(cv.nombreOriginal, style = MaterialTheme.typography.bodyLarge)
                Text(
                    // fechaLarga y no fechaCorta: `subidoEn` es un
                    // INSTANTE (lleva hora y zona), y fechaCorta espera
                    // una fecha de calendario -- le devolvía "--".
                    text = stringResource(
                        R.string.perfil_cv_detalle,
                        tamanoLegible(cv.tamanoBytes),
                        DateFormats.fechaLarga(cv.subidoEn)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = onDescargar, enabled = !descargando) {
                        Text(stringResource(R.string.perfil_cv_descargar))
                    }
                    TextButton(onClick = onBorrar) {
                        Text(stringResource(R.string.perfil_cv_borrar))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onElegir,
                enabled = !subiendo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (cv == null) R.string.perfil_cv_subir else R.string.perfil_cv_reemplazar
                    )
                )
            }
            Text(
                text = stringResource(R.string.perfil_cv_ayuda),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            error?.let { mensaje ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = mensaje.resolver(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * El nombre que el sistema le da al fichero elegido.
 *
 * Un `content://` no tiene nombre en la ruta: hay que preguntárselo al
 * proveedor. Si no lo da, se inventa uno en vez de mandar la URI entera,
 * que acabaría siendo el "nombre original" guardado en la base.
 */
private fun nombreDelUri(contexto: Context, uri: Uri): String {
    contexto.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val columna = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (columna >= 0 && cursor.moveToFirst()) {
            val nombre = cursor.getString(columna)
            if (!nombre.isNullOrBlank()) return nombre
        }
    }
    return "adjunto"
}

/**
 * "217 B", "12 KB", "3,4 MB".
 *
 * Dividir entre 1024 a secas dejaba un currículum de 217 bytes en
 * "0 KB", que no informa de nada: por debajo del kilobyte se enseñan
 * los bytes, y a partir del mega, un decimal.
 */
private fun tamanoLegible(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.forLanguageTag("es-ES"), "%.1f MB", bytes / (1024.0 * 1024.0))
}
