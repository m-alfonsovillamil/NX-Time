package com.nxtime.app.ui.atajos

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.nxtime.app.MainActivity
import com.nxtime.app.R
import com.nxtime.app.push.NotificacionesPush
import com.nxtime.app.ui.navegacion.DESTINO_AUSENCIAS
import com.nxtime.app.ui.navegacion.DESTINO_FICHAR
import com.nxtime.app.ui.navegacion.DESTINO_HISTORIAL

/**
 * Los accesos directos del icono (plan P12): mantener pulsado el icono de la
 * app y elegir «Fichar», «Pedir ausencia» o «Historial».
 *
 * Dinámicos y no en un `shortcuts.xml`: el XML necesita el paquete escrito
 * (`targetPackage`), que es otro en `dev` y cambia con el nombre; aquí sale
 * del propio `Context`. Abren `MainActivity` con la ruta como lo hace un push
 * (`EXTRA_RUTA`), así que sin sesión pasan por el login y luego van a su sitio.
 */
object Atajos {

    data class Atajo(
        val id: String,
        val ruta: String,
        @StringRes val corto: Int,
        @StringRes val largo: Int,
        @DrawableRes val icono: Int
    )

    val TODOS = listOf(
        Atajo("fichar", DESTINO_FICHAR, R.string.atajo_fichar, R.string.atajo_fichar_largo, R.drawable.ic_atajo_fichar),
        Atajo("ausencia", DESTINO_AUSENCIAS, R.string.atajo_ausencia, R.string.atajo_ausencia_largo, R.drawable.ic_atajo_ausencia),
        Atajo("historial", DESTINO_HISTORIAL, R.string.atajo_historial, R.string.atajo_historial_largo, R.drawable.ic_atajo_historial)
    )

    /** El intent que abre la app en [ruta]: el mismo camino que un push. */
    fun intent(context: Context, ruta: String): Intent =
        Intent(context, MainActivity::class.java)
            // Un acceso directo tiene que llevar acción; VIEW no, que es la de
            // los enlaces de la web y se lee de otra forma.
            .setAction(ACCION)
            .putExtra(NotificacionesPush.EXTRA_RUTA, ruta)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Los deja puestos. Se llama al arrancar: si ya estaban, los sustituye. */
    fun publicar(context: Context) {
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, TODOS.mapIndexed { orden, atajo ->
                ShortcutInfoCompat.Builder(context, atajo.id)
                    // El lanzador los ordena por rango: Fichar, el más usado, el primero.
                    .setRank(orden)
                    .setShortLabel(context.getString(atajo.corto))
                    .setLongLabel(context.getString(atajo.largo))
                    .setIcon(IconCompat.createWithResource(context, atajo.icono))
                    .setIntent(intent(context, atajo.ruta))
                    .build()
            })
        }
    }

    const val ACCION = "com.nxtime.app.ATAJO"
}
