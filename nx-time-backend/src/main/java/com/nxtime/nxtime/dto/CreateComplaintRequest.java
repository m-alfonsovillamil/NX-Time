package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.ComplaintCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Presentar una denuncia en el canal interno (Fase G).
 *
 * <b>{@code anonima} es obligatorio y no tiene valor por defecto</b>, y
 * eso es una decisión, no un olvido. Un defecto a false convertiría un
 * cliente que no manda el campo en un delator involuntario; uno a true
 * dejaría anónimas denuncias que su autor quería firmar, y el anonimato
 * no se puede deshacer después. Cuando la respuesta correcta depende de
 * algo que solo sabe quien denuncia, lo correcto es que no haya defecto.
 *
 * La descripción admite 4000 caracteres porque aquí se cuenta un hecho,
 * no se rellena un motivo: quien denuncia necesita sitio para fechas,
 * nombres y qué pasó.
 */
public record CreateComplaintRequest(

        @NotNull(message = "Hay que indicar la categoría de la denuncia.")
        ComplaintCategory categoria,

        @NotBlank(message = "Hay que describir los hechos que se denuncian.")
        @Size(max = 4000, message = "La descripción no puede pasar de 4000 caracteres.")
        String descripcion,

        @NotNull(message = "Hay que decir expresamente si la denuncia es anónima.")
        Boolean anonima
) {
}
