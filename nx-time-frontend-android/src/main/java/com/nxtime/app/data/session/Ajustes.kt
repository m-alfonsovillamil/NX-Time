package com.nxtime.app.data.session

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Qué tema pinta la aplicación. */
enum class Tema {
    /** Lo que diga el móvil. Es el valor por defecto. */
    SISTEMA,
    CLARO,
    OSCURO;

    companion object {
        fun de(valor: String?): Tema =
            entries.firstOrNull { it.name == valor } ?: SISTEMA
    }
}

/**
 * Las preferencias de la aplicación, las que no son de sesión.
 *
 * Viven en un `SharedPreferences` **propio** y no en el de
 * [SessionManager] a propósito: `clearAuthData()` borra sus claves al
 * cerrar sesión, y el tema que alguien eligió no es un dato de sesión.
 * Perderlo cada vez que se sale sería un fallo pequeño y muy molesto.
 *
 * El estado se expone como [StateFlow] porque lo leen sitios que no son
 * una pantalla —el tema lo aplica `MainActivity` sobre todo el árbol de
 * composición— y tienen que reaccionar sin reiniciar la app. Es el mismo
 * patrón que [com.nxtime.app.data.network.ArranqueEnFrio].
 */
class Ajustes(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(NOMBRE_PREFS, Context.MODE_PRIVATE)

    private val _tema = MutableStateFlow(Tema.de(prefs.getString(KEY_TEMA, null)))
    val tema: StateFlow<Tema> = _tema.asStateFlow()

    private val _informesDeErrores =
        MutableStateFlow(prefs.getBoolean(KEY_INFORMES, true))

    /**
     * Si los cierres de la app se mandan a Sentry. Encendido por defecto,
     * y se puede apagar: son datos que salen del móvil de alguien.
     */
    val informesDeErrores: StateFlow<Boolean> = _informesDeErrores.asStateFlow()

    private val _huella = MutableStateFlow(prefs.getBoolean(KEY_HUELLA, false))

    /**
     * Si al abrir la aplicación con la sesión guardada se pide la huella.
     *
     * **Apagado por defecto**, y activarlo exige la contraseña: si no, a
     * quien cogiera el móvil desbloqueado le bastaría con activarlo y su
     * propia huella para blindar la cuenta de otra persona.
     */
    val huella: StateFlow<Boolean> = _huella.asStateFlow()

    fun cambiarHuella(activa: Boolean) {
        _huella.value = activa
        prefs.edit().putBoolean(KEY_HUELLA, activa).apply()
    }

    fun cambiarTema(nuevo: Tema) {
        _tema.value = nuevo
        prefs.edit().putString(KEY_TEMA, nuevo.name).apply()
    }

    fun cambiarInformesDeErrores(activos: Boolean) {
        _informesDeErrores.value = activos
        prefs.edit().putBoolean(KEY_INFORMES, activos).apply()
    }

    companion object {
        const val NOMBRE_PREFS = "NXTIME_AJUSTES"
        private const val KEY_TEMA = "tema"
        private const val KEY_INFORMES = "informes_de_errores"
        private const val KEY_HUELLA = "entrar_con_huella"
    }
}
