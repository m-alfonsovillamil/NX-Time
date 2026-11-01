package com.nxtime.app.data.sinconexion

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.data.dto.AvisoDeEnvio
import com.nxtime.app.data.dto.EnvioSinConexionRequest
import com.nxtime.app.data.dto.FichajePendiente
import com.nxtime.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException

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
            if (!repositorio.getRegistroActivo().isSuccessful) return ResultadoDelEnvio.REINTENTAR

            while (true) {
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
        val cuerpo = respuesta.body()
        if (!respuesta.isSuccessful || cuerpo == null) return false

        val nombrados = cuerpo.resultados.map { it.idCliente }.toSet()
        if (nombrados.isEmpty()) return false

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
