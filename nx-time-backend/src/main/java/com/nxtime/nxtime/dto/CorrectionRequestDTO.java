package com.nxtime.nxtime.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Pedir que se corrijan las horas de un fichaje (Fase E).
 *
 * Es casi el mismo cuerpo que tenía {@code TimeEntryCorrectionRequest},
 * y a propósito: lo que cambia no es lo que se manda sino lo que pasa
 * después — antes se aplicaba, ahora se pide.
 *
 * El motivo es obligatorio y no es burocracia: es lo único que la
 * persona que tiene que aprobar puede leer para decidir, y lo que queda
 * en la traza si algún día hay que explicar por qué un fichaje dice lo
 * que dice.
 */
public record CorrectionRequestDTO(

        @NotNull(message = "La hora de entrada propuesta es obligatoria.")
        Instant horaEntrada,

        @NotNull(message = "La hora de salida propuesta es obligatoria.")
        Instant horaSalida,

        @NotBlank(message = "Hay que explicar por qué se corrige el fichaje.")
        @Size(max = 500, message = "El motivo no puede pasar de 500 caracteres.")
        String motivo,

        /*
         * Opcionales (09/2026, ADR 015): una pausa que se pide añadir. Van las
         * dos o ninguna. Si la solicitud solo añade la pausa, horaEntrada y
         * horaSalida se mandan con los valores actuales del fichaje.
         */
        Instant pausaInicio,
        Instant pausaFin,

        /**
         * El reparto por proyecto que se propone junto con las horas (ADR 017),
         * o null si la corrección no toca proyectos.
         *
         * Hasta septiembre de 2026 aquí ponía que «lo valida quien lo arma
         * ({@code AllocationEditService}); aquí solo viaja». Era cierto cuando
         * la petición venía de ese servicio, y falso para quien llamara al
         * endpoint directamente — que es cualquiera con un cliente HTTP. Lo
         * valida {@code ValidadorDeReparto}, por los dos caminos.
         */
        @Valid
        @Size(max = 50, message = "El reparto no puede tener más de 50 líneas.")
        List<ProjectShare> reparto
) {

    /** Para las solicitudes que solo tocan horas, que son todas las de antes. */
    public CorrectionRequestDTO(Instant horaEntrada, Instant horaSalida, String motivo) {
        this(horaEntrada, horaSalida, motivo, null, null);
    }

    /**
     * Una línea del reparto propuesto: cuántos minutos van a ese proyecto.
     *
     * Mismas restricciones que {@code SetAllocationsRequest.Linea}, porque son
     * la misma cosa por dos caminos. Cero minutos es válido y significa sacar
     * el proyecto del reparto.
     */
    public record ProjectShare(
            @Positive(message = "Falta el proyecto.")
            long proyectoId,

            @PositiveOrZero(message = "Los minutos no pueden ser negativos.")
            long minutos
    ) {
    }

    /** Lo que mandan las correcciones que no tocan proyectos. */
    public CorrectionRequestDTO(
            Instant horaEntrada, Instant horaSalida, String motivo, Instant pausaInicio, Instant pausaFin) {
        this(horaEntrada, horaSalida, motivo, pausaInicio, pausaFin, null);
    }
}
