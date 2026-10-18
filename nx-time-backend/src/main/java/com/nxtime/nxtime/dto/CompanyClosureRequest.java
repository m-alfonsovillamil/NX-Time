package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedir la baja de la empresa: hay que escribir su nombre (ADR 050). */
public record CompanyClosureRequest(
        @NotBlank(message = "Escribe el nombre de la empresa para confirmar la baja.")
        @Size(max = 255, message = "El nombre no puede pasar de 255 caracteres.")
        String confirmacion) {
}
