package com.nxtime.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.R
import com.nxtime.app.ui.atajos.Atajos
import com.nxtime.app.ui.navegacion.DESTINO_FICHAR
import com.nxtime.app.ui.theme.ColoresJornadaClaro
import com.nxtime.app.ui.theme.ColoresJornadaOscuro
import com.nxtime.app.ui.util.DateFormats
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * El widget de la jornada (plan P12): en la pantalla de inicio, si estás
 * trabajando, en pausa o sin jornada, con el color del botón de fichar
 * (`ColoresJornada`: se ve de lejos sin leer). Tocarlo abre Fichar.
 *
 * **No ficha él.** Fichar desde fuera de la app se saltaría lo que la app hace
 * antes: explicar la ubicación antes de pedirla, confirmar el fin de la
 * jornada, avisar sin conexión. El widget ahorra buscar la app y la pantalla,
 * que es casi todo el camino.
 */
class WidgetDeJornada : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            /*
             * El estado se lee DENTRO de la composición. Glance llama a
             * provideGlance una vez por sesión y la mantiene viva un rato: leído
             * fuera, un fichaje hecho mientras tanto no se veía (pasó en el
             * emulador). La versión cambia con cada ActualizarWidget.ahora.
             */
            val version by ActualizarWidget.version.collectAsState()
            val estado = remember(version) { estadoActual(context) }
            Contenido(context, estado)
        }
    }

    private fun estadoActual(context: Context): EstadoDelWidget {
        val app = context.applicationContext as NxTimeApplication
        val conSesion = app.sessionManager.fetchAuthToken() != null
        val jornada = app.sessionManager.correoDeLaSesion()?.let { app.colaDeFichajes.ultimaJornada(it) }
        // La de la empresa, como el resto de la app: no la del móvil.
        val zona = DateFormats.zona
        return estadoDelWidget(conSesion, jornada, LocalDate.now(zona), zona)
    }

    @Composable
    private fun Contenido(context: Context, estado: EstadoDelWidget) {
        val (fondo, texto) = colores(estado)
        val (titulo, detalle) = textos(context, estado)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(fondo)
                .cornerRadius(20.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable(actionStartActivity(Atajos.intent(context, DESTINO_FICHAR))),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(titulo, style = TextStyle(color = texto, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(detalle, style = TextStyle(color = texto, fontSize = 13.sp), maxLines = 2)
        }
    }

    private fun textos(context: Context, estado: EstadoDelWidget): Pair<String, String> = when (estado) {
        EstadoDelWidget.SinSesion ->
            context.getString(R.string.app_name) to context.getString(R.string.widget_sin_sesion)
        EstadoDelWidget.SinJornada ->
            context.getString(R.string.widget_sin_jornada) to context.getString(R.string.widget_tocar_para_fichar)
        is EstadoDelWidget.Trabajando ->
            context.getString(R.string.widget_trabajando) to context.getString(R.string.widget_desde, estado.desde)
        is EstadoDelWidget.EnPausa ->
            context.getString(R.string.widget_en_pausa) to context.getString(R.string.widget_desde, estado.desde)
    }

    /** El fondo y el texto, con su pareja para el tema oscuro: los mismos del botón de fichar. */
    private fun colores(estado: EstadoDelWidget): Pair<ColorProvider, ColorProvider> {
        val claro = ColoresJornadaClaro
        val oscuro = ColoresJornadaOscuro
        return when (estado) {
            is EstadoDelWidget.Trabajando ->
                ColorProvider(claro.trabajando, oscuro.trabajando) to ColorProvider(claro.onTrabajando, oscuro.onTrabajando)
            is EstadoDelWidget.EnPausa ->
                ColorProvider(claro.enPausa, oscuro.enPausa) to ColorProvider(claro.onEnPausa, oscuro.onEnPausa)
            EstadoDelWidget.SinJornada ->
                ColorProvider(claro.parado, oscuro.parado) to ColorProvider(claro.onParado, oscuro.onParado)
            // Sin sesión no hay estado de jornada: el azul de la marca.
            EstadoDelWidget.SinSesion ->
                ColorProvider(AZUL_NOCHE, AZUL_NOCHE_OSCURO) to ColorProvider(Color.White, SOBRE_AZUL_OSCURO)
        }
    }

    private companion object {
        val AZUL_NOCHE = Color(0xFF1F3A5F)
        val AZUL_NOCHE_OSCURO = Color(0xFFA9C7F2)
        val SOBRE_AZUL_OSCURO = Color(0xFF0B2A4D)
    }
}

/** Lo que Android llama para pintar y refrescar el widget. */
class WidgetDeJornadaReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetDeJornada()
}

/**
 * Repinta el widget, si hay alguno puesto. Lo llaman la cola al guardar la
 * jornada (cada vez que la app sabe algo nuevo de ella) y el cierre de sesión.
 * Fuera del hilo de quien llama, y sin que un fallo del widget suba: es un
 * extra, no puede romper fichar.
 */
object ActualizarWidget {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _version = MutableStateFlow(0L)

    /** Cambia cada vez que hay algo nuevo: el widget, si está pintado, vuelve a leer. */
    val version: StateFlow<Long> = _version.asStateFlow()

    fun ahora(context: Context) {
        _version.value += 1
        val aplicacion = context.applicationContext
        // Y si no hay sesión de Glance viva, que abra una con lo nuevo.
        alcance.launch { runCatching { WidgetDeJornada().updateAll(aplicacion) } }
    }
}
