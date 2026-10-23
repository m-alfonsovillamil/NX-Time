package com.nxtime.app.data.dto

/**
 * El código del segundo factor, con el desafío que devolvió el login (ADR 051
 * del backend). Sin `origen`: la app es ANDROID, el valor por defecto.
 */
data class PeticionSegundoPaso(
    val desafio: String,
    val codigo: String
)
