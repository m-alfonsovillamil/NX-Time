package com.nxtime.nxtime.dto

/**
 * DTO para recibir la petición de registro de un nuevo Gestor y Empresa.
 */
data class RegistroGestorRequest(
    val nombreEmpresa: String,
    val nombreGestor: String,
    val email: String,
    val password: String
)