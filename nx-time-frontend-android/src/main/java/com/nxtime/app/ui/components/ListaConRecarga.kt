package com.nxtime.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nxtime.app.R

/**
 * El armazón de las cuatro pantallas con lista.
 *
 * Reúne en un sitio lo que las cuatro repetían: el mismo `when` de
 * cargando/error/vacío/contenido. Y le añade lo que a ninguna le llegó a
 * dar tiempo: **recargar tirando hacia abajo**. Hasta ahora la única
 * forma de volver a pedir los datos era provocar un error y pulsar
 * "Reintentar", que no es una forma de refrescar, es un accidente.
 *
 * La primera carga enseña un esqueleto en vez de una rueda centrada: se
 * ve de inmediato QUÉ va a aparecer y cuánto, y la pantalla no da el
 * salto de "vacío con rueda" a "lista llena".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaConRecarga(
    cargando: Boolean,
    hayContenido: Boolean,
    onRecargar: () -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit
) {
    /*
     * El indicador de tirar solo se enseña cuando YA hay algo en
     * pantalla. En la primera carga manda el esqueleto: sacar los dos a
     * la vez pondría dos indicadores de progreso compitiendo.
     */
    PullToRefreshBox(
        isRefreshing = cargando && hayContenido,
        onRefresh = onRecargar,
        modifier = modifier.fillMaxSize()
    ) {
        if (cargando && !hayContenido) {
            EsqueletoDeLista()
        } else {
            contenido()
        }
    }
}

/**
 * Marcas de posición mientras llega la primera tanda.
 *
 * Late suavemente para que se lea como "esto está cargando" y no como
 * "esto son tarjetas vacías". Una sola animación compartida por todas
 * las filas: una por fila costaría cuatro veces más sin verse mejor.
 */
@Composable
fun EsqueletoDeLista(
    filas: Int = 4,
    alturaDeFila: Int = 120,
    modifier: Modifier = Modifier
) {
    // Se resuelve fuera del `semantics`, que no es composable.
    val textoCargando = stringResource(R.string.cargando)
    val transicion = rememberInfiniteTransition(label = "esqueleto")
    val opacidad by transicion.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "opacidadEsqueleto"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(16.dp))
            // Un lector de pantalla no debe leer cuatro rectángulos: se
            // anuncia una sola vez que se está cargando.
            .semantics { contentDescription = textoCargando },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(filas) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(alturaDeFila.dp)
                    .alpha(opacidad),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {}
        }
        Spacer(Modifier.height(4.dp))
    }
}

