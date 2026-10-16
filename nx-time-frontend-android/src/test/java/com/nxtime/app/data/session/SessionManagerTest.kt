package com.nxtime.app.data.session

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Los tokens de la sesión no se escriben tal cual en el disco (1.16).
 *
 * El cifrado de verdad usa el almacén de claves de Android, que en la JVM de
 * los tests no existe: aquí va uno de mentira que se reconoce a simple vista.
 * Lo que se prueba es lo que decide [SessionManager] alrededor: qué acaba en
 * las preferencias, que quien actualiza no tenga que volver a entrar, y qué
 * pasa cuando el dispositivo no puede cifrar o ha perdido la clave.
 */
@RunWith(RobolectricTestRunner::class)
class SessionManagerTest {

    private val contexto: Context = RuntimeEnvironment.getApplication()
    private val prefs = contexto.getSharedPreferences("NXTIME_PREFS", Context.MODE_PRIVATE)

    /** Le da la vuelta al texto: no protege nada, pero lo guardado deja de contener el token. */
    private val delReves = object : CifradoDeLaSesion {
        override fun cifrar(texto: String): String = texto.reversed()
        override fun descifrar(cifrado: String): String = cifrado.reversed()
    }

    private fun sesion(cifrado: CifradoDeLaSesion = delReves) = SessionManager(contexto, cifrado = cifrado)

    private fun guardado(clave: String): String? = prefs.getString(clave, null)

    @Test
    fun `lo que se escribe en el disco no es el token y al leerlo sale el de verdad`() {
        val sesion = sesion()

        sesion.saveAuthData("el-jwt", "el-refresh", "Ana")

        assertEquals("c1:twj-le", guardado("auth_token"))
        assertEquals("c1:hserfer-le", guardado("refresh_token"))
        assertEquals("el-jwt", sesion.fetchAuthToken())
        assertEquals("el-refresh", sesion.fetchRefreshToken())
    }

    @Test
    fun `al volver a abrir la app la sesion sigue ahi`() {
        sesion().saveAuthData("el-jwt", "el-refresh", "Ana")

        val alReabrir = sesion()

        assertEquals("el-jwt", alReabrir.fetchAuthToken())
        assertEquals("el-refresh", alReabrir.fetchRefreshToken())
        assertEquals("Ana", alReabrir.fetchUserName())
    }

    @Test
    fun `renovar el token tambien lo guarda cifrado`() {
        val sesion = sesion()
        sesion.saveAuthData("el-jwt", "el-refresh", "Ana")

        sesion.actualizarTokens("otro-jwt", "otro-refresh")

        assertEquals("c1:twj-orto", guardado("auth_token"))
        assertEquals("c1:hserfer-orto", guardado("refresh_token"))
        assertEquals("otro-jwt", sesion().fetchAuthToken())

        sesion.updateAccessToken("uno-mas")
        assertEquals("c1:sam-onu", guardado("auth_token"))
        assertEquals("uno-mas", sesion.fetchAuthToken())
        assertEquals("otro-refresh", sesion.fetchRefreshToken())
    }

    /** Quien actualiza desde la 1.15 trae los tokens sin cifrar, y no tiene por qué volver a entrar. */
    @Test
    fun `una sesion guardada por una version anterior se conserva y queda cifrada`() {
        prefs.edit()
            .putString("auth_token", "jwt-de-antes")
            .putString("refresh_token", "refresh-de-antes")
            .putString("user_name", "Ana")
            .commit()

        val sesion = sesion()

        assertEquals("jwt-de-antes", sesion.fetchAuthToken())
        assertEquals("refresh-de-antes", sesion.fetchRefreshToken())
        assertEquals("c1:setna-ed-twj", guardado("auth_token"))
        assertEquals("c1:setna-ed-hserfer", guardado("refresh_token"))
        // Y la siguiente vez ya se lee de lo cifrado.
        assertEquals("jwt-de-antes", sesion().fetchAuthToken())
    }

    @Test
    fun `sin sesion guardada no hay nada que leer ni que cifrar`() {
        val sesion = sesion()

        assertNull(sesion.fetchAuthToken())
        assertNull(sesion.fetchRefreshToken())
        assertFalse(prefs.contains("auth_token"))
    }

    /** Un almacén de claves estropeado no puede dejar a nadie sin poder entrar. */
    @Test
    fun `si el dispositivo no puede cifrar la sesion se guarda como antes y funciona`() {
        val sinCifrado = object : CifradoDeLaSesion {
            override fun cifrar(texto: String): String? = null
            override fun descifrar(cifrado: String): String? = null
        }
        val sesion = sesion(sinCifrado)

        sesion.saveAuthData("el-jwt", "el-refresh", "Ana")

        assertEquals("el-jwt", guardado("auth_token"))
        assertEquals("el-jwt", sesion(sinCifrado).fetchAuthToken())
        assertEquals("el-refresh", sesion(sinCifrado).fetchRefreshToken())
    }

    /** La clave no sale del dispositivo: si se pierde, lo guardado no vale y se vuelve a entrar. */
    @Test
    fun `si lo cifrado ya no se puede leer no hay sesion`() {
        sesion().saveAuthData("el-jwt", "el-refresh", "Ana")
        val claveNueva = object : CifradoDeLaSesion {
            override fun cifrar(texto: String): String = "x$texto"
            override fun descifrar(cifrado: String): String? = null
        }

        val sesion = sesion(claveNueva)

        assertNull(sesion.fetchAuthToken())
        assertNull(sesion.fetchRefreshToken())
        // Y se puede volver a entrar encima.
        sesion.saveAuthData("jwt-nuevo", "refresh-nuevo", "Ana")
        assertEquals("jwt-nuevo", sesion.fetchAuthToken())
        assertEquals("c1:xjwt-nuevo", guardado("auth_token"))
    }

    @Test
    fun `cerrar la sesion la borra del disco y de la memoria`() {
        var avisado = false
        val sesion = SessionManager(contexto, alCerrarSesion = { avisado = true }, cifrado = delReves)
        sesion.saveAuthData("el-jwt", "el-refresh", "Ana")

        sesion.clearAuthData()

        assertNull(sesion.fetchAuthToken())
        assertNull(sesion.fetchRefreshToken())
        assertFalse(prefs.contains("auth_token"))
        assertFalse(prefs.contains("refresh_token"))
        assertNull(sesion().fetchAuthToken())
        assertTrue(avisado)
    }
}
