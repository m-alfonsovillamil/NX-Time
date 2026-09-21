package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Comprobar la contraseña de quien ya está dentro, sin abrir sesión (Fase A11).
 *
 * Existe por la huella de la app: para activarla hay que confirmar la
 * contraseña, y hasta ahora eso se hacía llamando a {@code /auth/login}. Un
 * login completo para responder "sí o no" tenía tres efectos que nadie quería:
 *
 * <ul>
 *   <li>emitía un par de tokens nuevos y los guardaba encima de los que había;</li>
 *   <li>dejaba vivo en el servidor el refresh anterior, que ya no iba a usar
 *       nadie -- sesiones abiertas acumulándose sin que se cerraran nunca;</li>
 *   <li>consumía el límite de intentos de {@code /auth/login}, así que activar
 *       la huella varias veces podía dejar a alguien sin poder entrar.</li>
 * </ul>
 *
 * Con la rotación de refresh tokens el segundo punto pasa de molesto a
 * incorrecto, así que esto deja de ser una comodidad y pasa a hacer falta.
 */
@Schema(description = "Comprobar la contraseña propia, sin emitir tokens")
public record VerifyPasswordRequest(

        @Schema(description = "La contraseña actual de quien hace la petición")
        @NotBlank(message = "La contraseña es obligatoria.")
        String contrasena
) {
}
