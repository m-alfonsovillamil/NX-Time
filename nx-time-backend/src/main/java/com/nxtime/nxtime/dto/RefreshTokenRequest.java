package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cuerpo de /auth/refresh (pedir un access token nuevo) y de
 * /auth/logout (revocar la sesión): ambos solo necesitan el refresh
 * token.
 *
 * Opcional desde la fase W1 (ADR 030): la app Android lo manda aquí, y el
 * navegador no lo tiene -- viaja en la cookie {@code nx_refresh}, que el
 * JavaScript de la página no puede leer. Quién decide si falta es el
 * controlador: sin cuerpo y sin cookie, 400.
 */
public record RefreshTokenRequest(

        @Schema(description = "El refresh token. Lo manda la app; el navegador lo lleva en la cookie nx_refresh "
                + "y no manda cuerpo.")
        String refreshToken
) {
}
