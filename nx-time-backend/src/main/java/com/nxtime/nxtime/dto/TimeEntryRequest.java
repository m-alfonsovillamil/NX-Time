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
        Long proyectoId,

        /**
         * Solo en INICIO, y solo si la empresa la pregunta: presencial o a
         * distancia (ADR 055). Opcional aunque la pregunte: una app que no la
         * conoce tiene que poder fichar igual, y la jornada queda sin ella.
         */
        com.nxtime.nxtime.domain.Modalidad modalidad
) {
    /** Lo que mandan las acciones sin proyecto y las versiones de la app anteriores a la 1.4. */
    public TimeEntryRequest(TimeEntryAction tipo) {
        this(tipo, null, null);
    }

    /** Lo de antes del ADR 055: sin modalidad. */
    public TimeEntryRequest(TimeEntryAction tipo, Long proyectoId) {
        this(tipo, proyectoId, null);
    }
}
