package com.nxtime.app.data.session

import android.content.Context
import android.content.SharedPreferences

/**
 * Clase auxiliar para guardar datos de sesión en el móvil
 */

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("NXTIME_PREFS", Context.MODE_PRIVATE)

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
        editor.commit()
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
        prefs.edit().putString(KEY_AUTH_TOKEN, token).commit()
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
        editor.commit()
    }
}
