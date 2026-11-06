package com.nxtime.app.data.sinconexion

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.data.dto.AvisoDeEnvio
import com.nxtime.app.data.dto.EnvioSinConexionRequest
import com.nxtime.app.data.dto.ErrorDelEnvio
import com.nxtime.app.data.dto.FichajePendiente
import com.nxtime.app.data.network.ApiErrorParser
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import retrofit2.Response

/** Qué hacer después de un intento de envío. */
enum class ResultadoDelEnvio {
    /** No queda nada que mandar con esta sesión, o no se puede avanzar hasta volver a la app. */
    TERMINADO,

    /** Sin red, sin sesión válida o el servidor caído: se vuelve a intentar más tarde. */
    REINTENTAR
}

/**
 * Manda la cola hasta vaciarla. Sin Android, para probarla en la JVM; el Worker de
 * abajo solo la llama.
 *
 * **Lo único que saca un fichaje de la cola es una respuesta del servidor que lo
 * nombra.** Si la respuesta se pierde se reenvía, y el servidor lo reconoce por su
 * `idCliente` y contesta lo mismo (`yaRecibido`).
 */
class EnvioDeFichajes(
    private val repositorio: AuthRepository,
    private val cola: ColaDeFichajes,
    private val entorno: EntornoDelMovil,
    private val dueno: () -> String?
) {

    suspend fun enviar(): ResultadoDelEnvio {
        val quien = dueno() ?: return ResultadoDelEnvio.TERMINADO
        try {
            // Despierta el servidor (Render puede estar dormido) antes de calcular
            // las horas: lo que tarde en contestar no debe contar como tiempo pasado.
            if (cola.pendientes(quien).isEmpty()) return ResultadoDelEnvio.TERMINADO
            val despierto = repositorio.getRegistroActivo()
            if (!despierto.isSuccessful) {
                anotarRechazo(quien, despierto)
                return ResultadoDelEnvio.REINTENTAR
            }

            while (true) {
                // Despertar a Render puede tardar un minuto, y en ese rato cabe
                // cerrar sesión y entrar con otra cuenta. El token va en cada
                // petición: sin esta comprobación, lo de quien fichó saldría con
                // la sesión de otra persona y se le apuntaría a ella.
                if (dueno() != quien) return ResultadoDelEnvio.TERMINADO
                val lote = cola.pendientes(quien).take(ColaDeFichajes.MAXIMO)
                if (lote.isEmpty()) return ResultadoDelEnvio.TERMINADO
                if (!enviarLote(quien, lote)) return ResultadoDelEnvio.REINTENTAR
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ResultadoDelEnvio.REINTENTAR
        }
    }

    private suspend fun enviarLote(quien: String, lote: List<FichajePendiente>): Boolean {
        val arranque = entorno.arranque()
        val elapsed = entorno.elapsedMs()
        val respuesta = repositorio.enviarFichajesSinConexion(
            EnvioSinConexionRequest(lote.map { ReglaDelEnvio.paraEnviar(it, arranque, elapsed) })
        )
        if (!respuesta.isSuccessful) {
            anotarRechazo(quien, respuesta)
            return false
        }
        val cuerpo = respuesta.body() ?: return false

        val nombrados = cuerpo.resultados.map { it.idCliente }.toSet()
        if (nombrados.isEmpty()) return false
        cola.guardarErrorDelEnvio(quien, null)

        // Primero los avisos: si la app muere entre las dos escrituras, se reenvía
        // y como mucho se avisa dos veces, pero no se pierde un rechazo.
        val porId = lote.associateBy { it.idCliente }
        cola.anadirAvisos(
            cuerpo.resultados.mapNotNull { r ->
                val fichaje = porId[r.idCliente]
                if (fichaje == null || r.resultado == APLICADO) return@mapNotNull null
                AvisoDeEnvio(
                    idCliente = r.idCliente,
                    dueno = quien,
                    tipo = fichaje.tipo,
                    horaDelDispositivo = fichaje.horaDelDispositivo,
                    aRevisar = r.resultado == A_REVISAR,
                    motivo = r.motivo
                )
            }
        )
        cola.guardarUltimaJornada(quien, cuerpo.jornada)
        cola.quitar(nombrados)
        return true
    }

    /**
     * Un 4xx no se arregla esperando (una cuenta sin permiso, una petición que el
     * servidor no admite): se sigue reintentando, porque un fichaje no se tira,
     * pero la pantalla lo dice. Antes reintentaba sin avisar a nadie.
     *
     * Un 5xx, un 408 o un 429 pasan solos: de esos no se avisa. Un 401 tampoco:
     * la sesión ya ha caído y la app ha vuelto al login, y anotarlo enseñaría
     * un «sesión caducada» viejo justo después de volver a entrar (visto en el
     * emulador el 6/11/2026).
     */
    private fun anotarRechazo(quien: String, respuesta: Response<*>) {
        val codigo = respuesta.code()
        if (codigo !in 400..499 || codigo in setOf(401, 408, 429)) return
        cola.guardarErrorDelEnvio(quien, ErrorDelEnvio(codigo, ApiErrorParser.detalleDe(respuesta)))
    }

    private companion object {
        const val APLICADO = "APLICADO"
        const val A_REVISAR = "A_REVISAR"
    }
}

/** El envío en segundo plano: con red, aunque la app esté cerrada, y con reintentos. */
class EnvioDeFichajesWorker(contexto: Context, parametros: WorkerParameters) :
    CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NxTimeApplication
        return when (app.envioDeFichajes.enviar()) {
            ResultadoDelEnvio.TERMINADO -> Result.success()
            ResultadoDelEnvio.REINTENTAR -> Result.retry()
        }
    }
}
