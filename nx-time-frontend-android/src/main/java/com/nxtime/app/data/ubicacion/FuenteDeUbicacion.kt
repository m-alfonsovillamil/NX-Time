package com.nxtime.app.data.ubicacion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.nxtime.app.data.dto.EstadoUbicacion
import com.nxtime.app.data.dto.UbicacionDelFichaje
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** De dónde sale la ubicación al fichar: el móvil, o un doble en los tests. */
fun interface FuenteDeUbicacion {
    /** **Nunca falla ni bloquea más de unos segundos**: sin ubicación se ficha igual (ADR 056). */
    suspend fun tomar(): UbicacionDelFichaje
}

/**
 * Una sola posición, al entrar o al salir, con `LocationManager` y sin Play
 * Services: así funciona en cualquier Android, también en un Huawei, y el GPS
 * no necesita cobertura de datos. Nada de seguimiento: solo en primer plano, y
 * se deja de escuchar en cuanto hay respuesta.
 *
 * Se pregunta al GPS y a la red a la vez. La red contesta antes pero con menos
 * precisión, así que se espera mientras no haya una posición buena, con el tope
 * de [TOPE_MS], y si no llega se usa la mejor que haya.
 */
class UbicacionDelMovil(private val contexto: Context) : FuenteDeUbicacion {

    private val tienePermiso: Boolean
        get() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED }

    override suspend fun tomar(): UbicacionDelFichaje {
        if (!tienePermiso) return UbicacionDelFichaje(EstadoUbicacion.DENEGADA)
        return try {
            mejorPosicion()?.let(::aUbicacion) ?: UbicacionDelFichaje(EstadoUbicacion.NO_DISPONIBLE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Un fabricante que falla raro, o el permiso retirado justo ahora: se ficha igual.
            UbicacionDelFichaje(EstadoUbicacion.NO_DISPONIBLE)
        }
    }

    @SuppressLint("MissingPermission") // Comprobado en `tomar`, y lo que lance se recoge allí.
    private suspend fun mejorPosicion(): Location? {
        val gestor = contexto.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val proveedores = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in gestor.allProviders && gestor.isProviderEnabled(it) }
        if (proveedores.isEmpty()) return null

        val respuestas = Channel<Location?>(proveedores.size)
        val cancelar = CancellationSignal()
        var mejor: Location? = null
        try {
            proveedores.forEach { proveedor ->
                LocationManagerCompat.getCurrentLocation(
                    gestor, proveedor, cancelar, ContextCompat.getMainExecutor(contexto)
                ) { respuestas.trySend(it) }
            }
            withTimeoutOrNull(TOPE_MS) {
                repeat(proveedores.size) {
                    val posicion = respuestas.receive() ?: return@repeat
                    mejor = masPrecisa(mejor, posicion)
                    if (esBuena(posicion)) return@withTimeoutOrNull
                }
            }
        } finally {
            cancelar.cancel()
        }
        return mejor
    }

    private fun masPrecisa(a: Location?, b: Location): Location =
        if (a == null || b.accuracy < a.accuracy) b else a

    private fun esBuena(posicion: Location) = posicion.hasAccuracy() && posicion.accuracy <= PRECISION_BUENA_M

    private fun aUbicacion(posicion: Location) = UbicacionDelFichaje(
        estado = EstadoUbicacion.CAPTURADA,
        latitud = posicion.latitude,
        longitud = posicion.longitude,
        precisionM = posicion.accuracy.roundToInt().takeIf { posicion.hasAccuracy() }
    )

    private companion object {
        const val TOPE_MS = 8_000L
        const val PRECISION_BUENA_M = 100f
    }
}
