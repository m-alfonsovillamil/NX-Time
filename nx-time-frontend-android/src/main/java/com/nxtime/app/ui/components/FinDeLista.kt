package com.nxtime.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nxtime.app.R
import com.nxtime.app.ui.util.EstadoDePaginas

/**
 * El final de una lista paginada (Fase A7): cuando asoma, pide la página
 * siguiente.
 *
 * Funciona porque una `LazyColumn` solo compone lo que se ve: este elemento
 * entra en composición cuando se llega abajo, y su `LaunchedEffect` pide más.
 * La clave es la página que toca, así que si la lista es corta y el final
 * sigue a la vista tras cargar, vuelve a pedir la siguiente.
 *
 * Si la última petición falló no se reintenta solo —sin red se quedaría
 * pidiendo en bucle—: se ofrece un botón.
 */
fun LazyListScope.finDeLista(paginas: EstadoDePaginas, onCargarMas: () -> Unit) {
    if (!paginas.hayMas) return
    item(key = "fin-de-lista") {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (paginas.fallo) {
                TextButton(onClick = onCargarMas) {
                    Text(stringResource(R.string.lista_cargar_mas_reintentar))
                }
            } else {
                LaunchedEffect(paginas.siguiente) { onCargarMas() }
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}
