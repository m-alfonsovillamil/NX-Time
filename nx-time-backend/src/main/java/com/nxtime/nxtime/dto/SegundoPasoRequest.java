package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El segundo paso del login de quien tiene segundo factor (ADR 051): el
 * desafío que devolvió {@code /auth/login} y el código.
 */
@Schema(description = "El código del segundo factor, con el desafío que devolvió el login")
public record SegundoPasoRequest(

        @Schema(description = "El 'desafio' de la respuesta de /auth/login. Vale cinco minutos")
        @NotBlank(message = "Falta el desafío: vuelve a entrar con tu contraseña.")
        @Size(max = 1024, message = "El desafío no es válido.")
        String desafio,

        @Schema(description = "Las 6 cifras de la aplicación de autenticación, o uno de los códigos de recuperación",
                example = "123456")
        @NotBlank(message = "Escribe el código.")
        @Size(max = 32, message = "El código no es correcto.")
        String codigo,

        @Schema(description = "ANDROID, IOS o WEB, como en /auth/login. Con WEB el refresh va en la cookie")
        String origen
) {
}
