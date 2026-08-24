package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.TimeEntryAction;
import jakarta.validation.constraints.NotNull;

/**
 * La app envía esto al backend al pulsar el botón de fichar.
 */
public record TimeEntryRequest(

        @NotNull(message = "La acción de fichaje es obligatoria.")
        TimeEntryAction tipo
) {
}
