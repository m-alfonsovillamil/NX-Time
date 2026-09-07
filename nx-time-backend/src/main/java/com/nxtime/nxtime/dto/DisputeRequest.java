package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El dueño del fichaje no acepta la corrección que le proponen.
 *
 * El motivo es obligatorio porque es lo que RRHH lee para resolver en
 * firme: una disputa sin explicación no le da a quien decide nada sobre
 * lo que decidir. La base lo exige también
 * ({@code ck_correcciones_disputa_con_motivo}).
 */
public record DisputeRequest(

        @NotBlank(message = "Hay que explicar por qué no estás de acuerdo con la corrección.")
        @Size(max = 500, message = "El motivo no puede pasar de 500 caracteres.")
        String motivo
) {
}
