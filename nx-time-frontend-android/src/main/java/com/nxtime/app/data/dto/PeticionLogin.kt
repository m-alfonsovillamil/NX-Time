package com.nxtime.app.data.dto

// Datos que la app enviará al backend para el login
data class PeticionLogin(
    val email: String,
    val contrasena: String
)