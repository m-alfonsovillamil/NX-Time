package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo que hace falta para añadir la cuenta a la aplicación de autenticación
 * (ADR 051): el QR, ya dibujado en SVG para que ningún cliente necesite una
 * librería, y el secreto, para quien no pueda escanearlo. No queda activo
 * hasta confirmarlo con un código.
 */
@Schema(description = "El secreto nuevo, para la aplicación de autenticación")
public record ActivacionSegundoFactorResponse(
        @Schema(description = "El secreto en Base32, para escribirlo a mano") String secreto,
        @Schema(description = "La dirección otpauth:// que lleva el QR") String uri,
        @Schema(description = "El QR con esa dirección, en SVG") String qr) {
}
