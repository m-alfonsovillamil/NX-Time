package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dejar de recibir push en este dispositivo (Fase B5).
 *
 * El token va en el cuerpo y no en la URL: es un identificador de un aparato
 * de una persona, y las URL acaban en los registros de acceso.
 */
public record UnregisterPushDeviceRequest(@NotBlank @Size(max = 4096) String token) {
}
