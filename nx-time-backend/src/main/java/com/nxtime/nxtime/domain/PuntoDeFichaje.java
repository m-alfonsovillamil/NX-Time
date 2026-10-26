package com.nxtime.nxtime.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Dónde se fichó una entrada o una salida (V52, ADR 056). Solo en ese momento:
 * NX Time no sigue a nadie.
 *
 * <p>Las coordenadas van redondeadas a 4 decimales (unos 11 m): para saber si
 * se fichó en la obra basta, y no es un punto exacto. A los 12 meses se borran
 * ({@code ConservacionDeDatos}) y queda el estado.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PuntoDeFichaje {

    /** Por qué hay o no hay ubicación. */
    public enum Estado {
        /** Se tomó; las coordenadas, si no han pasado 12 meses. */
        CAPTURADA,
        /** La persona no dio permiso. Se fichó igual. */
        DENEGADA,
        /** No se pudo: sin señal, sin GPS, o tardó demasiado. Se fichó igual. */
        NO_DISPONIBLE
    }

    static final double DECIMALES = 10_000d;

    @Enumerated(EnumType.STRING)
    private Estado estado;

    private Double latitud;

    private Double longitud;

    private Integer precisionM;

    /** Una ubicación tomada, ya redondeada. */
    public static PuntoDeFichaje capturada(double latitud, double longitud, Integer precisionM) {
        return new PuntoDeFichaje(Estado.CAPTURADA, redondear(latitud), redondear(longitud), precisionM);
    }

    /** Sin coordenadas, diciendo por qué. */
    public static PuntoDeFichaje sin(Estado estado) {
        return new PuntoDeFichaje(estado, null, null, null);
    }

    static double redondear(double grados) {
        return Math.round(grados * DECIMALES) / DECIMALES;
    }
}
