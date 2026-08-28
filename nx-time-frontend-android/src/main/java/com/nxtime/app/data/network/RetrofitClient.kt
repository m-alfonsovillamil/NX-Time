package com.nxtime.app.data.network

import com.nxtime.app.BuildConfig
import com.nxtime.app.data.dto.RefreshTokenRequest
import com.nxtime.app.data.session.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/*
 * Prepara todo lo necesario para que Retrofit funcione.
 */
class RetrofitClient(
    private val sessionManager: SessionManager
) {

    /*
     * La dirección del backend ya no está escrita a fuego aquí: viene
     * del sabor de compilación (Fase 11, ver build.gradle.kts).
     *   - dev  -> http://10.0.2.2:8080/ (el localhost del PC visto
     *            desde el emulador)
     *   - prod -> la URL del despliegue, por HTTPS
     */
    private val BASE_URL = BuildConfig.BASE_URL

    /*
     * Interceptor de "logging".
     *
     * En release NO registra nada (Level.NONE): con Level.BODY se
     * vuelcan a logcat las peticiones y respuestas ENTERAS, incluidos
     * el token JWT y la contraseña que viaja en el login. En un APK
     * distribuido eso es una fuga de credenciales que cualquier app con
     * acceso a los logs podría aprovechar.
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    /*
     * Aquí creamos nuestro interceptor personalizado y le pasamos el SessionManager para que pueda coger el token.
     */
    private val authInterceptor = AuthInterceptor(sessionManager)

    /*
     * Cliente "desnudo" (sin authInterceptor ni el Authenticator de más
     * abajo) solo para llamar a /auth/refresh sin volver a entrar en el
     * propio mecanismo de refresco -- si llevara el authenticator, un
     * 401 al refrescar podría intentar refrescar otra vez sin fin.
     */
    private val refreshRetrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(OkHttpClient.Builder().addInterceptor(loggingInterceptor).build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val refreshApiService: ApiService by lazy {
        refreshRetrofit.create(ApiService::class.java)
    }

    /*
     * Desde la Fase 4 del backend, el access token dura poco (15 min) a
     * propósito. Cuando una petición responde 401, OkHttp llama a este
     * Authenticator ANTES de devolver la respuesta al código que hizo la
     * llamada: si conseguimos un access token nuevo con el refresh
     * token guardado, OkHttp reintenta la petición original sola, con
     * el token nuevo -- el resto de la app no tiene que enterarse de que
     * esto ha pasado. Si el refresh token también ha caducado o es
     * inválido, se cierra la sesión local y la petición sigue fallando
     * con 401 (la pantalla que la lanzó deberá redirigir al login).
     */
    private val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
        if (yaSeReintento(response)) {
            return@Authenticator null
        }

        val refreshToken = sessionManager.fetchRefreshToken() ?: return@Authenticator null

        val nuevoAccessToken = try {
            val respuesta = runBlocking { refreshApiService.refrescarToken(RefreshTokenRequest(refreshToken)) }
            if (respuesta.isSuccessful) respuesta.body()?.token else null
        } catch (e: Exception) {
            null
        }

        if (nuevoAccessToken == null) {
            sessionManager.clearAuthData()
            return@Authenticator null
        }

        sessionManager.updateAccessToken(nuevoAccessToken)
        response.request.newBuilder()
            .header("Authorization", "Bearer $nuevoAccessToken")
            .build()
    }

    /*
     * Evita reintentar sin fin: si esta petición ya se reintentó una vez
     * (hay una respuesta previa en la cadena), nos rendimos en vez de
     * volver a intentar el refresco.
     */
    private fun yaSeReintento(response: Response): Boolean {
        var reintentos = 0
        var previa = response.priorResponse
        while (previa != null) {
            reintentos++
            previa = previa.priorResponse
        }
        return reintentos >= 1
    }

    /*
     * Aquí construimos el "motor" HTTP
     */
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    /*
     * Aquí construimos Retrofit.
     */
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    /*
     * Esta es la variable pública que usará el resto de la app.
     */
    val instance: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
