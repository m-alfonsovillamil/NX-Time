package com.nxtime.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nxtime.app.NxTimeApplication

/**
 * Por donde entra un push (Fase B5, ADR 028).
 *
 * Los mensajes son solo de datos, así que llegan aquí siempre, con la app
 * abierta o no, y es aquí donde se decide si se enseñan: con el ajuste
 * encendido y con sesión. Sin sesión, nunca: el móvil puede ser ya de otra
 * persona, y su token en Google puede no haberse invalidado todavía.
 */
class NxTimeMessagingService : FirebaseMessagingService() {

    private val registro: RegistroDePush
        get() = (application as NxTimeApplication).registroDePush

    override fun onNewToken(token: String) {
        registro.tokenNuevo(token)
    }

    override fun onMessageReceived(mensaje: RemoteMessage) {
        if (!registro.debeEnsenar()) return
        val datos = mensaje.data
        NotificacionesPush.mostrar(
            contexto = this,
            tipo = datos["tipo"].orEmpty(),
            titulo = datos["titulo"] ?: getString(com.nxtime.app.R.string.app_name),
            cuerpo = datos["cuerpo"].orEmpty(),
            ruta = datos["ruta"]?.takeIf { it.isNotBlank() }
        )
    }
}
