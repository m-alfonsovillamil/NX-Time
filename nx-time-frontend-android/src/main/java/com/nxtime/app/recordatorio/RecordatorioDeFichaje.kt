package com.nxtime.app.recordatorio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nxtime.app.MainActivity
import com.nxtime.app.NxTimeApplication
import com.nxtime.app.R
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * El aviso de fichar: cuándo se programa y qué se enseña.
 *
 * La decisión de si toca o no está en [ReglaDelRecordatorio], que no sabe
 * de Android y se prueba en la JVM. Aquí queda lo que necesita sistema
 * operativo: preguntar al servidor por el fichaje activo y notificar.
 */
object RecordatorioDeFichaje {

    const val CANAL = "recordatorio-fichaje"
    private const val TRABAJO_ENTRADA = "recordatorio-entrada"
    private const val TRABAJO_SALIDA = "recordatorio-salida"

    /**
     * Programa los dos avisos diarios, o los cancela si [activo] es false.
     *
     * Se vuelve a llamar cada vez que cambian las horas: `UPDATE` reemplaza
     * el trabajo anterior en vez de dejar dos avisos compitiendo.
     */
    fun programar(context: Context, activo: Boolean, entrada: String, salida: String) {
        val work = WorkManager.getInstance(context)
        if (!activo) {
            work.cancelUniqueWork(TRABAJO_ENTRADA)
            work.cancelUniqueWork(TRABAJO_SALIDA)
            return
        }
        encolar(work, TRABAJO_ENTRADA, TipoDeAviso.ENTRADA, entrada)
        encolar(work, TRABAJO_SALIDA, TipoDeAviso.SALIDA, salida)
    }

    private fun encolar(work: WorkManager, nombre: String, tipo: TipoDeAviso, horaTexto: String) {
        val hora = ReglaDelRecordatorio.hora(horaTexto) ?: return
        val retardo = ReglaDelRecordatorio.minutosHasta(hora, LocalTime.now())

        val peticion = PeriodicWorkRequestBuilder<RecordatorioWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(retardo, TimeUnit.MINUTES)
            .setInputData(workDataOf(RecordatorioWorker.TIPO to tipo.name))
            .build()

        work.enqueueUniquePeriodicWork(nombre, ExistingPeriodicWorkPolicy.UPDATE, peticion)
    }

    /**
     * El canal, que en Android 8+ es lo que decide si el aviso suena y si
     * el usuario puede silenciarlo por separado. Se crea al programar y no
     * al arrancar: sin recordatorio no hay canal que enseñar en los ajustes
     * del sistema.
     */
    fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL,
            context.getString(R.string.recordatorio_canal),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.recordatorio_canal_detalle) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }
}

/**
 * Pregunta si hace falta avisar y, solo entonces, avisa.
 *
 * Es un `CoroutineWorker` porque la consulta al servidor es `suspend`. Si
 * la petición falla —sin red, servidor dormido— **no se reintenta**: el
 * aviso de las 09:30 no sirve a las 11:00, y `Result.success()` deja el
 * trabajo periódico en pie para mañana.
 */
class RecordatorioWorker(
    context: Context,
    parametros: WorkerParameters
) : CoroutineWorker(context, parametros) {

    override suspend fun doWork(): Result {
        val aplicacion = applicationContext as NxTimeApplication
        val tipo = inputData.getString(TIPO)?.let { TipoDeAviso.valueOf(it) } ?: return Result.success()

        // Sin sesión no hay a quién avisar, y preguntar daría un 401.
        if (aplicacion.sessionManager.fetchAuthToken() == null) return Result.success()

        val abierta = try {
            val respuesta = aplicacion.authRepository.getRegistroActivo()
            if (!respuesta.isSuccessful) return Result.success()
            ReglaDelRecordatorio.jornadaAbierta(respuesta.body())
        } catch (_: Exception) {
            return Result.success()
        }

        if (!ReglaDelRecordatorio.toca(tipo, abierta, LocalDate.now().dayOfWeek)) {
            return Result.success()
        }

        notificar(tipo)
        return Result.success()
    }

    private fun notificar(tipo: TipoDeAviso) {
        val contexto = applicationContext
        RecordatorioDeFichaje.crearCanal(contexto)

        val titulo = if (tipo == TipoDeAviso.ENTRADA) R.string.recordatorio_entrada_titulo
        else R.string.recordatorio_salida_titulo
        val texto = if (tipo == TipoDeAviso.ENTRADA) R.string.recordatorio_entrada_texto
        else R.string.recordatorio_salida_texto

        val abrir = android.app.PendingIntent.getActivity(
            contexto,
            0,
            Intent(contexto, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val aviso = NotificationCompat.Builder(contexto, RecordatorioDeFichaje.CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(contexto.getString(titulo))
            .setContentText(contexto.getString(texto))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()

        try {
            NotificationManagerCompat.from(contexto).notify(tipo.ordinal, aviso)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones (Android 13+) no se avisa y no
            // pasa nada más: el ajuste lo pide al activarse, pero se puede
            // revocar desde el sistema en cualquier momento.
        }
    }

    companion object {
        const val TIPO = "tipo"
    }
}
