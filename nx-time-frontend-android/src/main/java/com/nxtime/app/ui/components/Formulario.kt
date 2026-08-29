package com.nxtime.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nxtime.app.R

/**
 * Armazón de las pantallas con barra superior y flecha de volver.
 *
 * Cierra uno de los hallazgos del repaso: las trece pantallas anteriores
 * usaban un tema `NoActionBar` y **ninguna tenía barra**, así que no
 * había ni título ni forma de volver que no fuera el botón del sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaConBarra(
    titulo: String,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable (Modifier) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.volver)
                        )
                    }
                }
            )
        }
    ) { padding ->
        contenido(Modifier.padding(padding))
    }
}

/** Columna de formulario, con desplazamiento y hueco para el teclado. */
@Composable
fun ColumnaFormulario(
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp)
    ) {
        contenido()
    }
}

@Composable
fun CampoTexto(
    valor: String,
    onCambia: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    tipoTeclado: KeyboardType = KeyboardType.Text,
    ultimo: Boolean = false
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambia,
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = tipoTeclado,
            imeAction = if (ultimo) ImeAction.Done else ImeAction.Next
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

/**
 * Campo de contraseña con el ojo para mostrarla.
 *
 * Cada campo lleva su propio estado de visibilidad: la pantalla de
 * cambio de contraseña tiene tres, y compartir un único interruptor
 * entre ellos revelaría las tres a la vez.
 */
@Composable
fun CampoContrasena(
    valor: String,
    onCambia: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    ultimo: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = onCambia,
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (ultimo) ImeAction.Done else ImeAction.Next
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.login_ocultar_contrasena
                        else R.string.login_mostrar_contrasena
                    )
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

/** Botón principal de un formulario, con su estado de envío. */
@Composable
fun BotonPrincipal(
    texto: String,
    onClick: () -> Unit,
    cargando: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !cargando,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        if (cargando) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(texto)
        }
    }
}
