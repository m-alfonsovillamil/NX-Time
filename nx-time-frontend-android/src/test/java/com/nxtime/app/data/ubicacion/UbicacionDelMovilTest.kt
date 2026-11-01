package com.nxtime.app.data.ubicacion

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.LocationManager
import com.nxtime.app.data.dto.EstadoUbicacion
import com.nxtime.app.ui.components.enlaceAlMapa
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Sin ubicación se ficha igual (ADR 056): la fuente nunca falla, dice por qué no la hay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UbicacionDelMovilTest {

    private val app: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `sin permiso dice que se denego`() = runTest {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        assertEquals(EstadoUbicacion.DENEGADA, UbicacionDelMovil(app).tomar().estado)
    }

    @Test
    fun `con la ubicacion del movil apagada dice que no esta disponible`() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val gestor = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowOf(gestor).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowOf(gestor).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        assertEquals(EstadoUbicacion.NO_DISPONIBLE, UbicacionDelMovil(app).tomar().estado)
    }

    @Test
    fun `el enlace al mapa lleva punto decimal`() {
        assertEquals(
            "https://www.openstreetmap.org/?mlat=42.2406&mlon=-8.7207#map=17/42.2406/-8.7207",
            enlaceAlMapa(42.2406, -8.7207)
        )
    }
}
