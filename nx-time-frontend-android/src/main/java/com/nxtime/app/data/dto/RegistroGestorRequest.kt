package com.nxtime.app.data.dto

/**
 * DTO para enviar la información de registro de un nuevo Gestor y Empresa al backend.
 */
data class RegistroGestorRequest(
    val nombreEmpresa: String,
    val nombre: String,
    val apellidos: String,
    val email: String,
    val contrasena: String,
    /** La casilla de las condiciones y el contrato de encargo (ADR 046): sin ella el servidor no registra. */
    val aceptaCondiciones: Boolean
)