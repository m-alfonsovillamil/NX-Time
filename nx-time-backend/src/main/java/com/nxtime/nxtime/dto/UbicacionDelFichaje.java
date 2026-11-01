package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.PuntoDeFichaje;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Dónde se fichó (ADR 056): lo que manda el cliente al entrar o al salir, y lo
 * que se devuelve en el historial. Si no hay ubicación, el estado dice por qué
 * y las coordenadas van a null; se ficha igual.
 *
 * @param precisionM el radio de confianza que da el móvil o el navegador, en metros
 */
public record UbicacionDelFichaje(
        @NotNull(message = "Falta el estado de la ubicación.")
        PuntoDeFichaje.Estado estado,

        @DecimalMin(value = "-90", message = "La latitud va de -90 a 90.")
        @DecimalMax(value = "90", message = "La latitud va de -90 a 90.")
        Double latitud,

        @DecimalMin(value = "-180", message = "La longitud va de -180 a 180.")
        @DecimalMax(value = "180", message = "La longitud va de -180 a 180.")
        Double longitud,

        @PositiveOrZero(message = "La precisión no puede ser negativa.")
        Integer precisionM
) {

    /** Lo que se guarda: redondeada, y sin coordenadas si no se capturó o llegó a medias. */
    public PuntoDeFichaje aPunto() {
        if (estado == PuntoDeFichaje.Estado.CAPTURADA && latitud != null && longitud != null) {
            return PuntoDeFichaje.capturada(latitud, longitud, precisionM);
        }
        return PuntoDeFichaje.sin(estado == PuntoDeFichaje.Estado.CAPTURADA
                ? PuntoDeFichaje.Estado.NO_DISPONIBLE : estado);
    }
}
