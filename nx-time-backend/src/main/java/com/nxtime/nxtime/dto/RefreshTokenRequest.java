package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de /auth/refresh (pedir un access token nuevo) y de
 * /auth/logout (revocar la sesión): ambos solo necesitan el refresh
 * token.
 */
public record RefreshTokenRequest(

        @NotBlank(message = "El refresh token es obligatorio.")
        String refreshToken
) {
}
