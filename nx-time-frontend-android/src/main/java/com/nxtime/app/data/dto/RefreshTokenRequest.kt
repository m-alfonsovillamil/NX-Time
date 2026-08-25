package com.nxtime.app.data.dto

/**
 * Cuerpo de /auth/refresh y /auth/logout: ambos solo necesitan el
 * refresh token.
 */
data class RefreshTokenRequest(
    val refreshToken: String
)
