package com.nxtime.app.data.network

import com.nxtime.app.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/*
 * Recibe el SessionManager para poder acceder al token guardado.
 */
class AuthInterceptor(
    private val sessionManager: SessionManager
) : Interceptor {

    /*
     * Esta es la lógica principal del interceptor.
     */
    override fun intercept(chain: Interceptor.Chain): Response {

        // 1. Coge la petición original que se iba a enviar.
        val requestBuilder = chain.request().newBuilder()

        // 2. Pide el token al SessionManager.
        val token = sessionManager.fetchAuthToken()

        // 3. Si el token existe...
        if (token != null) {

            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        // 4. Deja que la petición continúe su camino.
        return chain.proceed(requestBuilder.build())
    }
}