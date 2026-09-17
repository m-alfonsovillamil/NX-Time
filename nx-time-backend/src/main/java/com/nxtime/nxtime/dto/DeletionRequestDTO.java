package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Size;

/**
 * Pedir que se borren mis datos (ADR 016).
 *
 * El motivo es opcional: el RGPD no obliga a dar razones para ejercer el
 * derecho de supresión, y pedirlas como requisito sería ponerle un peaje.
 */
public record DeletionRequestDTO(
        @Size(max = 500, message = "El motivo no puede pasar de 500 caracteres.")
        String motivo
) {
}
