package com.nxtime.nxtime.dto

/*
 * La app envía esto al backend al pulsar el botón de login.
 */
data class PeticionLogin(
    val email: String,
    val contrasena: String
)