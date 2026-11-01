package com.nxtime.app.data.sinconexion

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.provider.Settings
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nxtime.app.data.dto.AvisoDeEnvio
import com.nxtime.app.data.dto.FichajePendiente
import com.nxtime.app.data.dto.Registro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Los fichajes que esperan a que vuelva la red, por persona (ADR 054).
 *
 * Interfaz para poder probar el ViewModel y el envío en la JVM, sin Android.
 */
interface ColaDeFichajes {
    /** Cambia cuando algo sale de la cola o llega un aviso: la pantalla vuelve a preguntar. */
    val cambios: StateFlow<Long>

    fun pendientes(dueno: String): List<FichajePendiente>

    /** False si ya hay [MAXIMO]: es lo que el servidor admite por envío, y no debería pasar nunca. */
    fun anadir(fichaje: FichajePendiente): Boolean
    fun quitar(ids: Set<String>)

    /** La jornada abierta según la última respuesta del servidor: es la base sin red. */
    fun ultimaJornada(dueno: String): Registro?
    fun guardarUltimaJornada(dueno: String, jornada: Registro?)

    fun avisos(dueno: String): List<AvisoDeEnvio>
    fun anadirAvisos(avisos: List<AvisoDeEnvio>)
    fun quitarAviso(idCliente: String)

    companion object {
        const val MAXIMO = 50
    }
}

/**
 * En un `SharedPreferences` propio y con `commit`: un fichaje es un dato legal, y
 * si la app muere justo después de pulsar tiene que seguir ahí. Fuera de
 * [com.nxtime.app.data.session.SessionManager] a propósito: cerrar sesión no lo
 * borra, y se manda cuando esa misma persona vuelve a entrar. `allowBackup` está
 * apagado, así que no sale del móvil por la copia de seguridad.
 */
class ColaEnPreferencias(context: Context) : ColaDeFichajes {

    private val prefs: SharedPreferences = context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val cerrojo = Any()
    private val _cambios = MutableStateFlow(0L)
    override val cambios: StateFlow<Long> = _cambios.asStateFlow()

    private fun todos(): List<FichajePendiente> =
        leer(KEY_COLA, object : TypeToken<List<FichajePendiente>>() {})

    private fun todosLosAvisos(): List<AvisoDeEnvio> =
        leer(KEY_AVISOS, object : TypeToken<List<AvisoDeEnvio>>() {})

    private fun <T> leer(clave: String, tipo: TypeToken<List<T>>): List<T> =
        prefs.getString(clave, null)?.let { gson.fromJson(it, tipo) } ?: emptyList()

    override fun pendientes(dueno: String) = synchronized(cerrojo) { todos().filter { it.dueno == dueno } }

    override fun anadir(fichaje: FichajePendiente): Boolean = synchronized(cerrojo) {
        val actuales = todos()
        if (actuales.count { it.dueno == fichaje.dueno } >= ColaDeFichajes.MAXIMO) return false
        prefs.edit().putString(KEY_COLA, gson.toJson(actuales + fichaje)).commit()
    }

    override fun quitar(ids: Set<String>) {
        synchronized(cerrojo) {
            prefs.edit().putString(KEY_COLA, gson.toJson(todos().filterNot { it.idCliente in ids })).commit()
        }
        _cambios.value += 1
    }

    override fun ultimaJornada(dueno: String): Registro? =
        prefs.getString(KEY_JORNADA + dueno, null)?.let { gson.fromJson(it, Registro::class.java) }

    override fun guardarUltimaJornada(dueno: String, jornada: Registro?) {
        prefs.edit().putString(KEY_JORNADA + dueno, jornada?.let(gson::toJson)).apply()
    }

    override fun avisos(dueno: String) = synchronized(cerrojo) { todosLosAvisos().filter { it.dueno == dueno } }

    override fun anadirAvisos(avisos: List<AvisoDeEnvio>) {
        synchronized(cerrojo) {
            prefs.edit().putString(KEY_AVISOS, gson.toJson(todosLosAvisos() + avisos)).commit()
        }
        _cambios.value += 1
    }

    override fun quitarAviso(idCliente: String) {
        synchronized(cerrojo) {
            prefs.edit().putString(KEY_AVISOS, gson.toJson(todosLosAvisos().filterNot { it.idCliente == idCliente })).commit()
        }
        _cambios.value += 1
    }

    private companion object {
        const val NOMBRE_PREFS = "NXTIME_COLA"
        const val KEY_COLA = "cola"
        const val KEY_AVISOS = "avisos"
        const val KEY_JORNADA = "jornada_"
    }
}

/** Lo que hace falta del móvil para fichar sin conexión. Interfaz por lo mismo que [ColaDeFichajes]. */
interface EntornoDelMovil {
    fun hayRed(): Boolean
    fun ahora(): Instant
    fun elapsedMs(): Long
    fun arranque(): Int
    fun lanzarEnvio()
}

class EntornoAndroid(private val contexto: Context) : EntornoDelMovil {

    /** `VALIDATED`: una wifi sin salida a internet, o de portal cautivo, no cuenta como red. */
    override fun hayRed(): Boolean {
        val conectividad = contexto.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capacidades = conectividad.getNetworkCapabilities(conectividad.activeNetwork) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun ahora(): Instant = Instant.now()

    override fun elapsedMs(): Long = SystemClock.elapsedRealtime()

    override fun arranque(): Int =
        Settings.Global.getInt(contexto.contentResolver, Settings.Global.BOOT_COUNT, ARRANQUE_DESCONOCIDO)

    /**
     * Trabajo único: `KEEP` para que dos fichajes seguidos no lancen dos envíos.
     * El que está en marcha repite hasta vaciar la cola, y volver a la app lo
     * lanza otra vez para lo que entrara justo al acabar.
     */
    override fun lanzarEnvio() {
        val trabajo = OneTimeWorkRequestBuilder<EnvioDeFichajesWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(contexto).enqueueUniqueWork(TRABAJO, ExistingWorkPolicy.KEEP, trabajo)
    }

    private companion object {
        const val TRABAJO = "envio-de-fichajes"
    }
}
