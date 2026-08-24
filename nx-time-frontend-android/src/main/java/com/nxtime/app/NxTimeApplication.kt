package com.nxtime.app

import android.app.Application
import com.nxtime.app.data.network.ApiService
import com.nxtime.app.data.network.RetrofitClient
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.repository.AuthRepositoryImpl
import com.nxtime.app.data.session.SessionManager

/**
 * NxTimeApplication: Es la clase principal de la app. Se crea una sola vez cuando la app se inicia.
 */

class NxTimeApplication : Application() {

    /**
     * Declara las "herramientas" que estarán disponibles para todas las Activities y ViewModels.
     */

    lateinit var sessionManager: SessionManager
    lateinit var apiService: ApiService
    lateinit var authRepository: AuthRepository

    /**
     * Esta función se ejecuta 1 sola vez cuando la app arranca. Es el lugar perfecto para configurar nuestras herramientas.
     */

    override fun onCreate() {
        super.onCreate()

        // 1. Creamos el gestor de sesión (guarda el token).
        sessionManager = SessionManager(this)

        // 2. Creamos RetrofitClient y le pasamos el sessionManager
        val retrofitClient = RetrofitClient(sessionManager)

        // 3. Obtenemos la instancia de ApiService
        apiService = retrofitClient.instance

        // 4. Creamos el Repositorio principal, dándole acceso a la API y a la sesión
        authRepository = AuthRepositoryImpl(apiService, sessionManager)

    }
}