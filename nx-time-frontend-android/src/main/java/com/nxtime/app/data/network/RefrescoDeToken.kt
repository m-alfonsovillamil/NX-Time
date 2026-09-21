package com.nxtime.app.data.network

import com.nxtime.app.data.session.SessionManager

/**
 * Decide qué token usar cuando una petición ha recibido un 401, y se asegura
 * de que solo se pida uno nuevo a la vez.
 *
 * <b>Por qué existe como clase aparte.</b> Esta lógica vivía dentro del lambda
 * del `Authenticator` de [RetrofitClient], donde no se puede probar sin montar
 * un servidor: hace falta un `Response` de OkHttp, un cliente y una red. Lo que
 * falla aquí, en cambio, es puro razonamiento sobre concurrencia, y eso sí se
 * prueba con hilos y un contador. Mismo criterio que [com.nxtime.app.recordatorio.ReglaDelRecordatorio],
 * que se sacó del Worker por la misma razón.
 *
 * <b>El problema que resuelve.</b> Al abrir la app salen varias peticiones en
 * paralelo (el perfil, el contador de avisos, el estado de la jornada, el
 * resumen, los proyectos...). Pasados los 15 minutos de vida del access token,
 * **todas** reciben 401 casi a la vez, y antes cada una pedía su propio
 * refresco: cinco llamadas a `/auth/refresh` para conseguir cinco veces lo
 * mismo. Con el servidor de Render dormido eso son cinco esperas de hasta 300 s
 * ocupando otros tantos hilos de OkHttp.
 *
 * No corrompía el estado --el backend no rota el refresh token, así que las
 * cinco respuestas valían-- pero era un pico de carga y de latencia evitable. Y
 * deja de ser inofensivo en cuanto el refresh token rote: entonces la segunda
 * llamada llegaría con un token ya usado.
 */
class RefrescoDeToken(
    private val sessionManager: SessionManager,
    /**
     * Cómo se piden los tokens nuevos. Se inyecta para poder probar esta clase
     * sin red: en producción es la llamada a `/auth/refresh`.
     *
     * Recibe el refresh token actual y devuelve el par nuevo, o null si no se
     * pudo renovar.
     */
    private val pedirTokenNuevo: (String) -> TokensRenovados?
) {

    /**
     * Lo que devuelve /auth/refresh desde que el servidor rota (fase A11 del
     * backend): un access token **y un refresh nuevo**.
     *
     * Antes el refresh se reutilizaba y bastaba con guardar el access. Ahora el
     * que se presentó deja de valer, así que **hay que guardar los dos**: si se
     * guardara solo el access, la siguiente renovación llegaría con un token ya
     * rotado, el servidor lo leería como que alguien tiene una copia y cerraría
     * la sesión entera.
     */
    data class TokensRenovados(val accessToken: String, val refreshToken: String)

    private val cerrojo = Any()

    /**
     * El access token con el que reintentar, o null si hay que rendirse.
     *
     * Cuando devuelve null la sesión ya ha quedado expirada: quien llama solo
     * tiene que dejar que el 401 siga su camino.
     *
     * @param tokenQueFallo el valor de la cabecera `Authorization` que llevaba
     *     la petición rechazada, para distinguir "mi token ha caducado" de
     *     "otro hilo ya lo ha renovado mientras yo esperaba"
     */
    fun tokenParaReintentar(tokenQueFallo: String?): String? = synchronized(cerrojo) {
        // Mientras este hilo esperaba en el cerrojo, otro puede haber
        // refrescado ya. Si el token guardado no es el que falló, no hay nada
        // que pedir: basta reintentar con el actual. Sin esta comprobación, el
        // cerrojo solo serializaría las llamadas en lugar de evitarlas.
        val tokenActual = sessionManager.fetchAuthToken()
        if (tokenActual != null && tokenActual != tokenQueFallo) {
            return tokenActual
        }

        val refreshToken = sessionManager.fetchRefreshToken() ?: return null

        val nuevo = try {
            pedirTokenNuevo(refreshToken)
        } catch (e: Exception) {
            null
        }

        if (nuevo == null) {
            sessionManager.expirarSesion()
            return null
        }

        // Los DOS, y en una sola escritura: el refresh que se acaba de usar ya
        // no vale.
        sessionManager.actualizarTokens(nuevo.accessToken, nuevo.refreshToken)
        return nuevo.accessToken
    }
}
