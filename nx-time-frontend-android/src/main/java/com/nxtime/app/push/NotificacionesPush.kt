package com.nxtime.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.nxtime.app.MainActivity
import com.nxtime.app.R
import com.nxtime.app.data.session.Ajustes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Lo que el push necesita de Android: el canal y la notificación (Fase B5).
 */
object NotificacionesPush {

    const val CANAL = "avisos"

    /** El extra con el que la notificación abre la app: la `rutaDestino` del aviso. */
    const val EXTRA_RUTA = "com.nxtime.app.RUTA_DE_AVISO"

    /**
     * Pinta la notificación de un push.
     *
     * Una por TIPO de aviso, con el tipo como etiqueta: dos incidencias
     * seguidas se sustituyen en vez de apilarse, y el texto es genérico de
     * todas formas ("tienes incidencias nuevas"). El detalle está en la app.
     */
    fun mostrar(contexto: Context, tipo: String, titulo: String, cuerpo: String, ruta: String?) {
        crearCanal(contexto)
        val abrir = PendingIntent.getActivity(
            contexto,
            tipo.hashCode(),
            Intent(contexto, MainActivity::class.java)
                .putExtra(EXTRA_RUTA, ruta)
                // Si la app ya está abierta, la misma Activity recibe el
                // intent (onNewIntent) en vez de apilarse otra encima.
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notificacion = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()
        try {
            NotificationManagerCompat.from(contexto).notify(tipo, 0, notificacion)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones (Android 13+): la tarjeta de
            // Ajustes explica que hay que darlo, y el aviso sigue en la app.
        }
    }

    /** Como el del recordatorio: se crea al usarlo, para no enseñar un canal que nunca suena. */
    private fun crearCanal(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL,
            contexto.getString(R.string.push_canal),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = contexto.getString(R.string.push_canal_detalle) }
        contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }
}

/** [PreferenciasDePush] sobre los [Ajustes] de la app. */
class PreferenciasEnAjustes(private val ajustes: Ajustes) : PreferenciasDePush {
    override val activo: Boolean get() = ajustes.push.value
    override fun cambiar(activo: Boolean) = ajustes.cambiarPush(activo)
    override var token: String?
        get() = ajustes.tokenPush
        set(valor) {
            ajustes.tokenPush = valor
        }
}

/** [TokensDePush] con Firebase de verdad. */
class TokensDeFirebase : TokensDePush {

    override fun activar() {
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
    }

    override suspend fun token(): String? = suspendCancellableCoroutine { continuacion ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { tarea ->
            continuacion.resume(if (tarea.isSuccessful) tarea.result else null)
        }
    }

    override fun olvidar() {
        val messaging = FirebaseMessaging.getInstance()
        messaging.isAutoInitEnabled = false
        // Asíncrono y sin esperar: invalida el token en Google. Si no hay red
        // se pierde, y da igual: sin sesión la app no enseña nada.
        messaging.deleteToken()
    }
}
