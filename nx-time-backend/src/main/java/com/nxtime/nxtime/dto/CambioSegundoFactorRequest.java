package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Activar o desactivar el segundo factor (ADR 051): la contraseña y un código.
 * Las dos cosas, para que una sesión robada no baste: quien la tenga no sabe
 * la contraseña, y sin el móvil no tiene el código.
 */
@Schema(description = "La contraseña y un código del segundo factor")
public record CambioSegundoFactorRequest(

        @Schema(description = "La contraseña actual de quien hace la petición")
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 256, message = "La contraseña es demasiado larga.")
        String contrasena,

        @Schema(description = "Las 6 cifras de la aplicación de autenticación (o, al desactivarlo, un código de "
                + "recuperación)", example = "123456")
        @NotBlank(message = "Escribe el código.")
        @Size(max = 32, message = "El código no es correcto.")
        String codigo
) {
}
