package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Seguir una denuncia con su código, en el CUERPO y no en la URL (revisión de
 * seguridad del 1/10/2026, ADR 034). El código es la credencial de una
 * denuncia anónima, y una URL acaba en los logs de acceso, en el historial
 * del navegador y en las migas de Sentry.
 *
 * @param codigo el código de seguimiento que se dio al presentarla
 * @param texto solo al escribir en el expediente; null al consultarlo
 */
public record ComplaintTrackingRequest(

        @NotBlank(message = "El código de seguimiento es obligatorio.")
        @Size(max = 40, message = "Ese código no es válido.")
        String codigo,

        @Size(max = 2000, message = "El mensaje no puede pasar de 2000 caracteres.")
        String texto
) {
}
