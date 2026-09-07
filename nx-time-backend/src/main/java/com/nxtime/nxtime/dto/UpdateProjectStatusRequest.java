package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Cerrar o reabrir un proyecto.
 *
 * Va en su propio endpoint y no dentro de {@link ProjectRequest} para
 * que guardar un cambio de nombre no pueda reabrir sin querer un
 * proyecto cerrado, solo porque el formulario mandó de vuelta el valor
 * que tenía cargado. Es el mismo criterio que separa el estado de un
 * empleado de su ficha.
 *
 * {@code Boolean} y no {@code boolean}: con el primitivo, un cuerpo sin
 * el campo llegaría como {@code false} y cerraría el proyecto en
 * silencio en vez de dar un 400.
 */
public record UpdateProjectStatusRequest(

        @NotNull(message = "Hay que indicar si el proyecto queda activo o cerrado.")
        Boolean activo
) {
}
