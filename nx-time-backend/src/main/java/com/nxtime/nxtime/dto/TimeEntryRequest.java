package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.TimeEntryAction;
import jakarta.validation.constraints.NotNull;

/**
 * La app envía esto al backend al pulsar el botón de fichar.
 */
public record TimeEntryRequest(
        @NotNull(message = "La acción de fichaje es obligatoria.")
        TimeEntryAction tipo,

        /**
         * Solo en INICIO: en qué proyecto se va a trabajar (ADR 017). Opcional:
         * con un solo proyecto asignado se usa ese, y sin ninguno se ficha sin
         * proyecto. Las demás acciones lo ignoran.
         */
        Long proyectoId
) {
    /** Lo que mandan las acciones sin proyecto y las versiones de la app anteriores a la 1.4. */
    public TimeEntryRequest(TimeEntryAction tipo) {
        this(tipo, null);
    }
}
