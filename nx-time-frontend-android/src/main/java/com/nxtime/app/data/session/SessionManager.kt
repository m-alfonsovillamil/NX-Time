package com.nxtime.app.data.session

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Clase auxiliar para guardar datos de sesión en el móvil
 */

class SessionManager(
    context: Context,
    /**
     * Lo que hay que deshacer fuera de estas preferencias al cerrar sesión.
     *
     * Hoy cancela los recordatorios de fichaje, que viven en WorkManager y
     * sobreviven a la sesión: sin esto seguían encolados con las horas de
     * quien se fue, y si en ese móvil entra otra persona los hereda.
     *
     * Se recibe como función en vez de llamar a WorkManager desde aquí para
     * no atar el almacén de la sesión a la programación de tareas -- y para
     * que valga por los tres caminos de salida sin tener que acordarse en
     * cada uno.
     */
    private val alCerrarSesion: () -> Unit = {}
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("NXTIME_PREFS", Context.MODE_PRIVATE)

    private val _sesionCaducada = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Avisa de que la sesión murió sola, sin que el usuario la cerrara.
     *
     * Lo recoge el grafo de navegación para llevar al login. Antes no
     * existía: el `Authenticator` de `RetrofitClient` borraba el token
     * cuando el refresco fallaba y devolvía null, pero **ninguna pantalla
     * se enteraba**, así que la app se quedaba en "Mi jornada" enseñando
     * el nombre cacheado y un banner de error, sin forma de volver a
     * entrar salvo cerrando sesión a mano.
     *
     * `extraBufferCapacity = 1` para que emitir no bloquee ni se pierda
     * el aviso si llega mientras nadie está recogiendo todavía.
     */
    val sesionCaducada: SharedFlow<Unit> = _sesionCaducada.asSharedFlow()

    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ROLE = "user_role"
    }

    /**
     * Guarda el token de acceso, el refresh token, nombre y rol de forma síncrona.
     */
    fun saveAuthData(token: String, refreshToken: String, nombre: String, rol: String) {
        val editor = prefs.edit()
        editor.putString(KEY_AUTH_TOKEN, token)
        editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        editor.putString(KEY_USER_NAME, nombre)
        editor.putString(KEY_USER_ROLE, rol)
        // apply() y no commit(): esto se llama al entrar, desde el hilo
        // principal, y commit() escribe en disco de forma bloqueante justo
        // mientras la pantalla está pasando al inicio. apply() actualiza la
        // copia en memoria al instante --así que el fetchAuthToken() de la
        // petición siguiente ya ve el token-- y escribe en segundo plano.
        editor.apply()
    }

    /**
     * Obtiene el token JWT de acceso guardado.
     */
    fun fetchAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    /**
     * Obtiene el refresh token guardado.
     */
    fun fetchRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    /**
     * Reemplaza solo el token de acceso, tras renovarlo con /auth/refresh
     * (el refresh token no cambia).
     */
    fun updateAccessToken(token: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    /**
     * Obtiene el nombre del usuario guardado.
     */
    fun fetchUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    /**
     * Obtiene el rol del usuario guardado.
     */
    fun fetchUserRole(): String? {
        return prefs.getString(KEY_USER_ROLE, null)
    }

    /**
     * Borra todos los datos de sesión (para cerrar sesión).
     */
    fun clearAuthData() {
        val editor = prefs.edit()
        editor.remove(KEY_AUTH_TOKEN)
        editor.remove(KEY_REFRESH_TOKEN)
        editor.remove(KEY_USER_NAME)
        editor.remove(KEY_USER_ROLE)
        // Aquí SÍ se mantiene commit(), al revés que en saveAuthData: una
        // sesión que se cierra tiene que quedar cerrada en el disco antes de
        // seguir. Con apply(), si el proceso muere en ese instante, los tokens
        // seguirían escritos y quien cogiera el móvil después entraría solo.
        // Es una operación única y bloquear unos milisegundos sale barato.
        editor.commit()

        alCerrarSesion()
    }

    /**
     * Igual que [clearAuthData], pero además avisa por [sesionCaducada].
     *
     * Son dos métodos y no uno porque las dos salidas de la sesión no son
     * la misma cosa: cerrarla a mano ya navega al login desde la propia
     * pantalla, y emitir también ahí provocaría dos navegaciones
     * seguidas. Esta la usa solo el `Authenticator`, que es quien
     * descubre que el refresh token ya no vale.
     */
    fun expirarSesion() {
        clearAuthData()
        _sesionCaducada.tryEmit(Unit)
    }
}
