package com.nxtime.app.data.dto

/** Cuerpo de `POST /api/v1/dispositivos-push` (Fase B5). */
data class RegistroDispositivoPushRequest(
    val token: String,
    val plataforma: String = "ANDROID"
)

/** Cuerpo de `POST /api/v1/dispositivos-push/baja`: el token va en el cuerpo, no en la URL. */
data class BajaDispositivoPushRequest(val token: String)
