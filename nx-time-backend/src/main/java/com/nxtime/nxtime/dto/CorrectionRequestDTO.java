package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

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
        String motivo
) {
}
