package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Aprobar o rechazar una solicitud de corrección.
 *
 * {@code Boolean} y no {@code boolean}: con el primitivo, un cuerpo sin
 * el campo llegaría como {@code false} y RECHAZARÍA la solicitud en
 * silencio en vez de dar un 400.
 *
 * Al rechazar, el comentario es obligatorio — lo comprueba el servicio,
 * porque depende del valor de {@code aprobada} y eso no se puede
 * expresar con una anotación sobre un campo suelto. Es la misma regla
 * que ya rige al rechazar una ausencia: decir que no sin decir por qué
 * deja a la otra persona sin nada que hacer con la respuesta.
 */
public record ResolveCorrectionRequest(

        @NotNull(message = "Hay que indicar si la corrección se aprueba o se rechaza.")
        Boolean aprobada,

        @Size(max = 500, message = "El comentario no puede pasar de 500 caracteres.")
        String comentario
) {
}
