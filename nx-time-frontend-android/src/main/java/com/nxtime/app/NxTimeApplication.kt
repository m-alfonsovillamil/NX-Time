package com.nxtime.app

import android.app.Application
import com.nxtime.app.data.network.ApiService
import com.nxtime.app.data.network.ArranqueEnFrio
import com.nxtime.app.data.network.RetrofitClient
import com.nxtime.app.data.repository.AuthRepository
import com.nxtime.app.data.repository.AuthRepositoryImpl
import com.nxtime.app.data.session.Ajustes
import com.nxtime.app.data.session.SessionManager
import io.sentry.android.core.SentryAndroid

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

    /** Lo lee MainActivity para avisar de que el servidor está despertando. */
    lateinit var arranqueEnFrio: ArranqueEnFrio

    /**
     * Preferencias que NO son de sesión (tema, informes de errores). Van
     * en su propio almacén para que cerrar sesión no las borre.
     */
    lateinit var ajustes: Ajustes

    /**
     * Esta función se ejecuta 1 sola vez cuando la app arranca. Es el lugar perfecto para configurar nuestras herramientas.
     */

    override fun onCreate() {
        super.onCreate()

        // 0. Los ajustes, lo primero: deciden si Sentry llega a arrancar.
        ajustes = Ajustes(this)

        // 0.1. Sentry antes que el resto: un cierre mientras se monta la
        //      aplicación también tiene que llegar.
        iniciarSentry()

        // 1. Creamos el gestor de sesión (guarda el token).
        sessionManager = SessionManager(this)

        // 2. Creamos RetrofitClient y le pasamos el sessionManager, y el
        //    vigilante del arranque en frío que decide cuánto esperar.
        arranqueEnFrio = ArranqueEnFrio()
        val retrofitClient = RetrofitClient(sessionManager, arranqueEnFrio)

        // 3. Obtenemos la instancia de ApiService
        apiService = retrofitClient.instance

        // 4. Creamos el Repositorio principal, dándole acceso a la API y a la sesión
        authRepository = AuthRepositoryImpl(apiService, sessionManager)

    }

    /**
     * Sentry (paso 5 del piloto): los cierres de la app, a un sitio donde
     * verlos. Sin esto, un fallo en el móvil de alguien no deja rastro.
     *
     * Solo si el APK se compiló con DSN. Sin datos personales por defecto
     * (ni IP ni usuario), igual que en el backend. El entorno es el sabor,
     * "dev" o "prod", para no mezclar los cierres de una prueba en el
     * emulador con los de producción.
     */
    private fun iniciarSentry() {
        if (BuildConfig.SENTRY_DSN.isBlank()) return
        // Y solo si quien usa la app lo permite: son datos que salen de su
        // móvil. Apagarlo en caliente cierra Sentry (ver AjustesViewModel);
        // encenderlo tiene efecto al siguiente arranque, que es cuando se
        // puede inicializar.
        if (!ajustes.informesDeErrores.value) return
        SentryAndroid.init(this) { opciones ->
            opciones.dsn = BuildConfig.SENTRY_DSN
            opciones.environment = BuildConfig.FLAVOR
            opciones.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            opciones.isSendDefaultPii = false
        }
    }
}