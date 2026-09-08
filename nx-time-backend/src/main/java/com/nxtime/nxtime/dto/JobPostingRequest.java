package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Crear o editar una oferta interna (Fase H).
 *
 * <b>No lleva estado.</b> Publicar y cerrar son operaciones aparte
 * ({@code PATCH /ofertas/{id}/estado}) y no un campo más de este cuerpo:
 * con el estado aquí dentro, guardar un cambio de redacción publicaría
 * la oferta sin querer en cuanto alguien mandara el formulario entero.
 *
 * {@code fechaCierre} es opcional porque hay vacantes que se cierran
 * cuando aparece la persona, no en una fecha. Se valida que no sea
 * pasada en el servicio y no con {@code @Future}: al EDITAR una oferta
 * ya publicada, su fecha de cierre puede ser legítimamente de ayer.
 */
public record JobPostingRequest(

        @NotBlank(message = "La oferta necesita un título.")
        @Size(max = 150, message = "El título no puede pasar de 150 caracteres.")
        String titulo,

        @NotBlank(message = "Hay que describir el puesto.")
        @Size(max = 4000, message = "La descripción no puede pasar de 4000 caracteres.")
        String descripcion,

        @Size(max = 100, message = "El puesto no puede pasar de 100 caracteres.")
        String puesto,

        Long departamentoId,

        LocalDate fechaCierre
) {
}
