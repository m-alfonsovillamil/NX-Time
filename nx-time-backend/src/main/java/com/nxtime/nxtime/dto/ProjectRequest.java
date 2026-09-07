package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Alta y edición de un proyecto.
 *
 * No lleva {@code empresaId}: es siempre la de quien hace la petición
 * (ADR 006). Tampoco lleva {@code activo}: cerrar o reabrir un proyecto
 * es una operación distinta de editar sus datos, y va por su propio
 * endpoint — si viajara aquí, guardar un cambio de nombre reabriría sin
 * querer un proyecto cerrado por el simple hecho de que el formulario
 * mandó el valor que tenía cargado.
 *
 * Que {@code fechaFin} no sea anterior a {@code fechaInicio} lo
 * comprueba el servicio y además la base
 * ({@code ck_proyectos_fechas}); no se puede expresar con una anotación
 * de Bean Validation sobre un campo suelto.
 */
public record ProjectRequest(

        @NotBlank(message = "El código del proyecto es obligatorio.")
        @Size(max = 30, message = "El código no puede pasar de 30 caracteres.")
        String codigo,

        @NotBlank(message = "El nombre del proyecto es obligatorio.")
        @Size(max = 150, message = "El nombre no puede pasar de 150 caracteres.")
        String nombre,

        @Size(max = 500, message = "La descripción no puede pasar de 500 caracteres.")
        String descripcion,

        @NotNull(message = "La fecha de inicio es obligatoria.")
        LocalDate fechaInicio,

        LocalDate fechaFin
) {
}
