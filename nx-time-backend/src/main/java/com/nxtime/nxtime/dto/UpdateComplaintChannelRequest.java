package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Size;

/**
 * Designar al Responsable del canal de denuncias y decir cuál es la vía
 * verbal (ADR 048). Se guarda entero.
 *
 * @param responsableId una persona activa de la empresa, o null para que
 *     vuelvan a instruir los ADMIN
 */
public record UpdateComplaintChannelRequest(
        Long responsableId,

        @Size(max = 500, message = "La vía verbal no puede pasar de 500 caracteres.")
        String viaVerbal) {
}
