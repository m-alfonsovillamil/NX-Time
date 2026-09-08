package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.JobPostingStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Publicar, retirar o cerrar una oferta (Fase H).
 *
 * Va aparte del cuerpo que edita la oferta a propósito: publicar es lo
 * que la hace visible para toda la plantilla y lo que dispara el aviso,
 * así que tiene que ser un gesto propio y no un efecto colateral de
 * guardar un cambio de redacción.
 */
public record UpdateJobPostingStatusRequest(

        @NotNull(message = "Hay que indicar el nuevo estado.")
        JobPostingStatus estado
) {
}
