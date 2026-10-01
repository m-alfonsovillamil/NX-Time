package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que se puede cambiar de la empresa (fase Z2). Los dos campos van siempre:
 * es un formulario pequeño y se guarda entero.
 *
 * @param zonaHoraria nombre IANA ({@code Europe/Madrid}, {@code Atlantic/Canary}...).
 *   Lo valida el servicio contra las zonas que conoce Java.
 */
public record UpdateCompanySettingsRequest(
        @NotBlank(message = "El nombre de la empresa es obligatorio.")
        @Size(max = 255, message = "El nombre no puede pasar de 255 caracteres.")
        String nombre,

        @NotBlank(message = "La zona horaria es obligatoria.")
        @Size(max = 64, message = "La zona horaria no puede pasar de 64 caracteres.")
        String zonaHoraria) {
}
