package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Size;

/**
 * El motivo de rechazar o suspender una empresa (ADR 046). Opcional, pero si se
 * pone les llega a sus ADMIN en el correo y queda en el historial.
 */
public record PlatformActionRequest(
        @Size(max = 1000, message = "El motivo no puede pasar de 1000 caracteres.")
        String motivo
) {
}
