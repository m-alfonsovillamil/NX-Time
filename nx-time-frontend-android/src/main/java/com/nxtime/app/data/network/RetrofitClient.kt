package com.nxtime.app.data.network

import com.nxtime.app.data.session.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/*
 * Prepara todo lo necesario para que Retrofit funcione.
 */
class RetrofitClient(
    sessionManager: SessionManager
) {

    /*
     * Esta es la dirección IP del backend.
     */
    private val BASE_URL = "http://10.0.2.2:8080/"

    /*
     * Esto crea un interceptor de "logging"
     */
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    /*
     * Aquí creamos nuestro interceptor personalizado y le pasamos el SessionManager para que pueda coger el token.
     */
    private val authInterceptor = AuthInterceptor(sessionManager)

    /*
     * Aquí construimos el "motor" HTTP
     */
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
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