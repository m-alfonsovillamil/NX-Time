package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un código del segundo factor, para lo que solo pide eso (ADR 051). */
@Schema(description = "Un código del segundo factor")
public record CodigoSegundoFactorRequest(

        @Schema(description = "Las 6 cifras de la aplicación de autenticación, o uno de los códigos de recuperación",
                example = "123456")
        @NotBlank(message = "Escribe el código.")
        @Size(max = 32, message = "El código no es correcto.")
        String codigo
) {
}
