package com.nxtime.app.data.dto

/**
 * DTO para enviar la información de registro de un nuevo Gestor y Empresa al backend.
 */
data class RegistroGestorRequest(
    val nombreEmpresa: String,
    val nombreGestor: String,
    val email: String,
    val password: String
)