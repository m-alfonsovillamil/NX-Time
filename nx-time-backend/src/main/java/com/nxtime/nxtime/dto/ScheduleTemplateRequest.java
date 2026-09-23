package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Crear o editar una plantilla de horario semanal (Fase B1).
 *
 * Los tramos van en <b>minutos desde medianoche</b> del día en que empiezan:
 * 09:00 es 540, y un turno de 22:00 a 06:00 es {@code inicio = 1320, fin =
 * 1800}. Es lo que permite escribir el turno de noche en un solo tramo. Un día
 * sin tramos es libre en esta plantilla.
 */
public record ScheduleTemplateRequest(

        @NotBlank(message = "La plantilla necesita un nombre.")
        @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres.")
        String nombre,

        @Size(max = 500, message = "La descripción no puede pasar de 500 caracteres.")
        String descripcion,

        // El tope no es de negocio sino de cordura: siete días con tres tramos
        // cada uno son 21. Sin tope, una sola petición podría mandar miles.
        @NotNull(message = "Hay que indicar los tramos, aunque sea una lista vacía.")
        @Size(max = 50, message = "Una plantilla no puede tener más de 50 tramos.")
        List<@Valid @NotNull Tramo> tramos
) {

    public record Tramo(
            @Schema(description = "Día de la semana en ISO: 1 = lunes, 7 = domingo.", example = "1")
            @Min(value = 1, message = "El día de la semana va de 1 (lunes) a 7 (domingo).")
            @Max(value = 7, message = "El día de la semana va de 1 (lunes) a 7 (domingo).")
            int diaSemana,

            @Schema(description = "Minutos desde medianoche en que empieza, de 0 a 1439.", example = "540")
            int inicio,

            @Schema(description = "Minutos desde medianoche en que termina. Pasa de 1440 si cruza la "
                    + "medianoche.", example = "840")
            int fin
    ) {
    }
}
