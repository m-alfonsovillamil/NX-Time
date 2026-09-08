package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Mover una candidatura de estado (Fase H).
 *
 * El comentario es <b>obligatorio al descartar</b> y opcional en el
 * resto. La asimetría es la misma que en las correcciones de la fase E y
 * por el mismo motivo, aquí con una vuelta de tuerca: quien lo va a leer
 * es un compañero, sobre sí mismo, dentro de la empresa en la que sigue
 * trabajando mañana. Decirle que no sigue adelante sin una palabra más
 * es la peor forma de usar un canal de promoción interna.
 */
public record UpdateApplicationStatusRequest(

        @NotNull(message = "Hay que indicar el nuevo estado.")
        ApplicationStatus estado,

        @Size(max = 1000, message = "El comentario no puede pasar de 1000 caracteres.")
        String comentario
) {
}
