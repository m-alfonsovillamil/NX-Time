package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Mover una denuncia de estado (Fase G).
 *
 * La conclusión es obligatoria al cerrar — {@code RESUELTA} o
 * {@code ARCHIVADA} — y solo entonces. Archivar sin escribir en qué
 * quedó es lo que la Ley 2/2023 llama no responder, y da igual que el
 * desenlace sea que la denuncia no se sostenía: hay que decirlo.
 */
public record UpdateComplaintStatusRequest(

        @NotNull(message = "Hay que indicar el nuevo estado.")
        ComplaintStatus estado,

        @Size(max = 2000, message = "La conclusión no puede pasar de 2000 caracteres.")
        String conclusion
) {
}
