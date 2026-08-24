package com.nxtime.nxtime.dto

import com.nxtime.nxtime.dominio.Rol

/**
 * DTO que el backend devuelve tras un login o registro exitoso.
 */
data class RespuestaAutenticacion(
    val token: String,
    val nombre: String,
    val rol: Rol
)