package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * La app canjea por su sesión el código con el que volvió del navegador (ADR 036).
 *
 * @param codigo      el que llegó en {@code nxtime://sso?codigo=…}
 * @param verificador el valor aleatorio cuyo SHA-256 mandó la app al empezar. Es lo
 *                    que demuestra que quien canjea es quien empezó
 */
public record SsoExchangeRequest(
        @NotBlank(message = "Falta el código.")
        @Size(max = 200, message = "El código no es válido.")
        String codigo,

        @NotBlank(message = "Falta el verificador.")
        @Size(max = 200, message = "El verificador no es válido.")
        String verificador
) {
}
