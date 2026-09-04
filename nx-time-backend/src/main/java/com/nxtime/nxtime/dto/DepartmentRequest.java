package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta y renombrado de un departamento. */
public record DepartmentRequest(

        @NotBlank(message = "El nombre del departamento es obligatorio.")
        @Size(max = 100, message = "El nombre no puede pasar de 100 caracteres.")
        String nombre
) {
}
