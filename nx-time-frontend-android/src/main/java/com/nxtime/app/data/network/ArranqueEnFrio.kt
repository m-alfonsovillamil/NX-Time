package com.nxtime.app.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Sobrevivir al arranque en frío del backend (piloto, 09/2026).
 *
 * Render, en el plan gratuito, apaga el servicio tras 15 minutos sin
 * tráfico, y despertarlo lleva su tiempo: medido el 09/09/2026, un login
 * en frío tardó **160 s** frente a 0,8 s en caliente. La app usaba los
 * 10 s por defecto de OkHttp, así que la primera petición de cada mañana
 * fallaba siempre -- no iba lenta: no entraba. Y el error no ayudaba en
 * nada, porque reintentar vuelve a esperar al mismo servidor que se está
 * levantando.
 *
 * La espera larga NO se aplica a todo: en una pantalla normal, tres
 * minutos mirando una rueda son peores que un error. Tampoco a unos
 * endpoints concretos (el login, fichar): con la sesión guardada, lo
 * primero que se pide por la mañana es lo que cargue "Mi jornada", no el
 * login. Lo que decide es **cuánto hace de la última respuesta**: si la
 * app no ha sabido nada del servidor en [UMBRAL_DORMIDO_MS], puede estar
 * dormido y la petición espera lo que tarda en despertar; si no, rigen los
 * tiempos cortos.
 *
 * El workflow `keep-alive.yml` lo mantiene despierto casi siempre, pero
 * GitHub retrasa los `schedule` cuando tiene carga, así que esto no sobra.
 *
 * Si la espera larga pasa de [retardoAvisoMs], [despertando] se pone a
 * true para que la interfaz lo diga. Sin eso se ve una pantalla congelada,
 * y se cierra la app antes de que el servidor responda.
 */
class ArranqueEnFrio(
    private val reloj: () -> Long = System::currentTimeMillis,
    private val retardoAvisoMs: Long = RETARDO_AVISO_MS
) {

    private val ultimaRespuesta = AtomicLong(NUNCA)

    private val _despertando = MutableStateFlow(false)
    val despertando: StateFlow<Boolean> = _despertando.asStateFlow()

    /** Peticiones que llevan más de [retardoAvisoMs] esperando. */
    private var esperasLentas = 0

    /*
     * Hilo demonio: un avisador pendiente no puede impedir que el proceso
     * termine, ni en la app ni al acabar la suite de tests.
     */
    private val avisador = Executors.newSingleThreadScheduledExecutor { tarea ->
        Thread(tarea, "nxtime-arranque-en-frio").apply { isDaemon = true }
    }

    /**
     * Tiene que ir el PRIMERO de los interceptores: los tiempos que fija
     * con `withReadTimeout` solo alcanzan a lo que viene detrás en la
     * cadena.
     */
    val interceptor = Interceptor { chain ->
        if (!puedeEstarDormido()) {
            chain.proceed(chain.request()).also { anotar(it) }
        } else {
            val avisado = AtomicBoolean(false)
            val aviso = avisador.schedule(
                { if (avisado.compareAndSet(false, true)) cambiarEsperasLentas(+1) },
                retardoAvisoMs,
                TimeUnit.MILLISECONDS
            )
            try {
                chain
                    .withConnectTimeout(CONEXION_LARGA_S.toInt(), TimeUnit.SECONDS)
                    .withReadTimeout(LECTURA_LARGA_S.toInt(), TimeUnit.SECONDS)
                    .proceed(chain.request())
                    .also { anotar(it) }
            } finally {
                aviso.cancel(false)
                // Si el avisador ya había marcado esta petición como lenta,
                // el compareAndSet falla y toca descontarla.
                if (!avisado.compareAndSet(false, true)) cambiarEsperasLentas(-1)
            }
        }
    }

    /**
     * El cliente con los tiempos cortos y este interceptor ya puesto. Lo
     * usan los dos clientes de [RetrofitClient], también el del refresco
     * del token: por la mañana puede ser él quien se encuentre el servidor
     * dormido.
     */
    fun clienteBase(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(CONEXION_CORTA_S, TimeUnit.SECONDS)
        .readTimeout(LECTURA_CORTA_S, TimeUnit.SECONDS)
        .writeTimeout(LECTURA_CORTA_S, TimeUnit.SECONDS)
        .addInterceptor(interceptor)

    fun puedeEstarDormido(): Boolean {
        val ultima = ultimaRespuesta.get()
        return ultima == NUNCA || reloj() - ultima >= UMBRAL_DORMIDO_MS
    }

    /*
     * Un 5xx no demuestra que el servidor esté despierto: mientras Render
     * levanta la instancia, quien responde puede ser su proxy (502/503) y
     * no la aplicación. Cualquier otro código sí sale de Spring.
     */
    private fun anotar(respuesta: Response) {
        if (respuesta.code < 500) ultimaRespuesta.set(reloj())
    }

    private fun cambiarEsperasLentas(cambio: Int) = synchronized(this) {
        esperasLentas += cambio
        _despertando.value = esperasLentas > 0
    }

    companion object {
        /**
         * Render duerme el servicio a los 15 min sin tráfico. Se deja
         * margen porque el apagado no es al segundo, y equivocarse por
         * este lado solo cuesta esperar más de la cuenta a un servidor
         * que ya estaba despierto -- es decir, nada: responde enseguida.
         */
        const val UMBRAL_DORMIDO_MS = 10 * 60 * 1000L

        const val RETARDO_AVISO_MS = 3_000L

        /*
         * El arranque en frío NO es fijo. El 09/09/2026 un login tardó
         * 160 s; el 11/09, con 180 s de espera, la app se rindió y el
         * servidor ya respondía en 0,3 s poco después (entre 180 y ~210 s).
         * Se dejan 300 s de margen: equivocarse por arriba solo alarga una
         * espera que ya se está avisando, y por abajo es un login fallido
         * justo cuando el servidor iba a responder.
         */
        const val LECTURA_LARGA_S = 300L
        const val CONEXION_LARGA_S = 30L

        /*
         * Los de siempre eran los 10 s de OkHttp. Se suben algo porque,
         * con Render ya despierto, Neon puede estar suspendido y tarda unos
         * segundos en volver (HikariCP espera hasta 45 s en producción), y
         * porque un CV de 5 MB desde el móvil no siempre sube en 10 s.
         */
        const val CONEXION_CORTA_S = 15L
        const val LECTURA_CORTA_S = 30L

        private const val NUNCA = Long.MIN_VALUE
    }
}
