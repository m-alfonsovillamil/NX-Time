package com.nxtime.app.data.dto

/** Sin contraseña, igual que [CrearEmpleadoRequest] (ADR 014). */
data class CrearGestorRequest(
    val nombre: String,
    val email: String
)
