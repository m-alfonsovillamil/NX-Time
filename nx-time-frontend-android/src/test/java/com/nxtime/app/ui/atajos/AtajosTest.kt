package com.nxtime.app.ui.atajos

import androidx.core.content.pm.ShortcutManagerCompat
import com.nxtime.app.MainActivity
import com.nxtime.app.push.NotificacionesPush
import com.nxtime.app.ui.navegacion.rutaDeAviso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Los accesos directos del icono (plan P12). */
@RunWith(RobolectricTestRunner::class)
class AtajosTest {

    private val contexto = RuntimeEnvironment.getApplication()

    @Test
    fun `cada atajo lleva a una pantalla que la app sabe abrir`() {
        Atajos.TODOS.forEach { atajo ->
            assertTrue("${atajo.ruta} no tiene pantalla", rutaDeAviso(atajo.ruta) != null)
        }
    }

    @Test
    fun `se publican los tres, y abren la app con la ruta como un push`() {
        Atajos.publicar(contexto)

        val publicados = ShortcutManagerCompat.getDynamicShortcuts(contexto).sortedBy { it.rank }
        assertEquals(Atajos.TODOS.map { it.id }, publicados.map { it.id })
        publicados.zip(Atajos.TODOS).forEach { (publicado, atajo) ->
            val intent = publicado.intent
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertEquals(atajo.ruta, intent.getStringExtra(NotificacionesPush.EXTRA_RUTA))
            // VIEW es la de los enlaces de la web, que se leen de otra forma.
            assertNotEquals(android.content.Intent.ACTION_VIEW, intent.action)
        }
    }
}
