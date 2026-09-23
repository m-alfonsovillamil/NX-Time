package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.ScheduleExceptionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Un día que se sale de la plantilla (Fase B1).
 *
 * {@code LIBRE} no lleva tramos. {@code TRAMO} lleva uno o varios, y sustituyen
 * ese día a los de la plantilla: una jornada partida son dos. Los tramos, en
 * minutos desde medianoche, como en las plantillas.
 *
 * Los festivos y las ausencias NO se registran aquí: ya salen del calendario
 * laboral y de las ausencias aprobadas.
 */
public record ScheduleExceptionRequest(

        @NotNull(message = "Hay que decir de quién es la excepción.")
        Long usuarioId,

        @NotNull(message = "Hay que indicar el día.")
        LocalDate fecha,

        @NotNull(message = "Hay que indicar el tipo: LIBRE o TRAMO.")
        ScheduleExceptionType tipo,

        @Size(max = 6, message = "Un día no puede tener más de 6 tramos.")
        List<@Valid @NotNull Tramo> tramos,

        @Size(max = 300, message = "El motivo no puede pasar de 300 caracteres.")
        String motivo
) {

    public record Tramo(int inicio, int fin) {
    }
}
