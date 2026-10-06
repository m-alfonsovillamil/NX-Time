package com.nxtime.app.data.session

import android.content.Context
import android.content.SharedPreferences
import com.nxtime.app.ui.util.DateFormats
import kotlinx.coroutines.flow.StateFlow

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

    private val senalDeCaducidad = SenalDeSesionCaducada()

    /**
     * `true` mientras la sesión haya muerto sola (sin que el usuario la
     * cerrara) y nadie haya llevado todavía al login.
     *
     * Lo mira el grafo de navegación. Antes no existía: el `Authenticator`
     * de `RetrofitClient` borraba el token cuando el refresco fallaba y
     * devolvía null, pero **ninguna pantalla se enteraba**, así que la app se
     * quedaba en "Mi jornada" enseñando el nombre cacheado y un banner de
     * error, sin forma de volver a entrar salvo cerrando sesión a mano.
     *
     * Es un estado y no un aviso de una sola vez (octubre de 2026): el aviso
     * se perdía si la sesión caducaba antes de que hubiera pantalla, y la app
     * volvía a quedarse exactamente así. Ver [SenalDeSesionCaducada].
     */
    val sesionCaducada: StateFlow<Boolean> = senalDeCaducidad.pendiente

    /** El grafo de navegación ya ha llevado al login. */
    fun caducidadAtendida() = senalDeCaducidad.atendida()

    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_AUTHORITIES = "user_authorities"
        private const val KEY_ZONA = "empresa_zona_horaria"
    }

    init {
        // Una sesión guardada de antes: sus horas, desde el primer fotograma,
        // en la zona de su empresa (ADR 032). Sin la clave (una versión
        // anterior de la app), la de por defecto hasta el próximo refresco.
        DateFormats.fijarZona(prefs.getString(KEY_ZONA, null))
    }

    /**
     * Guarda el token de acceso, el refresh token, el nombre y los
     * permisos de forma síncrona.
     *
     * El **rol** ya no se guarda: desde la Fase C3 nada de la app lo
     * consulta. Lo que decidía —qué pantallas se ofrecen— lo dice ahora
     * `authorities`, resuelto por el servidor, y donde hay que escribir
     * el rol en pantalla se lee del perfil.
     */
    fun saveAuthData(
        token: String,
        refreshToken: String,
        nombre: String,
        authorities: Collection<String> = emptyList(),
        zonaHoraria: String? = null
    ) {
        val editor = prefs.edit()
        editor.putString(KEY_ZONA, zonaHoraria)
        editor.putString(KEY_AUTH_TOKEN, token)
        editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        editor.putString(KEY_USER_NAME, nombre)
        editor.putStringSet(KEY_AUTHORITIES, authorities.toSet())
        // apply() y no commit(): esto se llama al entrar, desde el hilo
        // principal, y commit() escribe en disco de forma bloqueante justo
        // mientras la pantalla está pasando al inicio. apply() actualiza la
        // copia en memoria al instante --así que el fetchAuthToken() de la
        // petición siguiente ya ve el token-- y escribe en segundo plano.
        editor.apply()
        DateFormats.fijarZona(zonaHoraria)
        // Una sesión nueva: si quedaba una caducidad sin atender, ya no aplica.
        senalDeCaducidad.atendida()
    }

    /**
     * La zona de la empresa, tras renovar el token: si la han cambiado en los
     * ajustes de la empresa, llega así sin volver a entrar (ADR 032).
     */
    fun actualizarZona(zonaHoraria: String) {
        prefs.edit().putString(KEY_ZONA, zonaHoraria).apply()
        DateFormats.fijarZona(zonaHoraria)
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
     * Guarda el par que devuelve `/auth/refresh` desde que el servidor rota.
     *
     * Los dos juntos y en una sola escritura, no dos llamadas seguidas: si el
     * proceso muriera entre medias quedaría un access nuevo con un refresh ya
     * rotado, y la siguiente renovación cerraría la sesión entera al
     * interpretarse como una reutilización.
     */
    fun actualizarTokens(token: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    /**
     * Obtiene el nombre del usuario guardado.
     */
    fun fetchUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    /**
     * Lo que el servidor dijo que esta persona puede hacer.
     *
     * Devuelve un conjunto vacío si no hay nada guardado, y eso significa
     * **sin permisos**, nunca "todos": una sesión abierta con una versión
     * anterior de la app no tiene esta clave, y lo correcto ahí es no
     * ofrecer nada de gestión hasta que [actualizarAuthorities] la rellene
     * (lo hace la pantalla de perfil) o la persona vuelva a entrar.
     *
     * Se copia el conjunto que devuelve `SharedPreferences`: la
     * documentación de Android dice explícitamente que no se debe modificar
     * el que entrega, y que su contenido no está garantizado si se guarda
     * la referencia.
     */
    fun fetchAuthorities(): Set<String> {
        return prefs.getStringSet(KEY_AUTHORITIES, emptySet()).orEmpty().toSet()
    }

    /**
     * Refresca los permisos sin tocar la sesión.
     *
     * Existe para dos casos: que a alguien le cambien el rol mientras tiene
     * la app abierta, y que una sesión abierta antes de esta versión no
     * tenga la clave todavía.
     */
    fun actualizarAuthorities(authorities: Collection<String>) {
        prefs.edit().putStringSet(KEY_AUTHORITIES, authorities.toSet()).apply()
    }

    /**
     * Borra todos los datos de sesión (para cerrar sesión).
     */
    fun clearAuthData() {
        val editor = prefs.edit()
        editor.remove(KEY_AUTH_TOKEN)
        editor.remove(KEY_REFRESH_TOKEN)
        editor.remove(KEY_USER_NAME)
        editor.remove(KEY_AUTHORITIES)
        editor.remove(KEY_ZONA)
        // Aquí SÍ se mantiene commit(), al revés que en saveAuthData: una
        // sesión que se cierra tiene que quedar cerrada en el disco antes de
        // seguir. Con apply(), si el proceso muere en ese instante, los tokens
        // seguirían escritos y quien cogiera el móvil después entraría solo.
        // Es una operación única y bloquear unos milisegundos sale barato.
        editor.commit()
        DateFormats.fijarZona(null)

        alCerrarSesion()
    }

    /**
     * Igual que [clearAuthData], pero además lo deja dicho en [sesionCaducada].
     *
     * Son dos métodos y no uno porque las dos salidas de la sesión no son
     * la misma cosa: cerrarla a mano ya navega al login desde la propia
     * pantalla, y emitir también ahí provocaría dos navegaciones
     * seguidas. Esta la usa solo el `Authenticator`, que es quien
     * descubre que el refresh token ya no vale.
     */
    fun expirarSesion() {
        clearAuthData()
        senalDeCaducidad.avisar()
    }
}
