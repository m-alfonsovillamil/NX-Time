package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.Size;

/**
 * Presentarse a una oferta interna (Fase H).
 *
 * <b>No lleva el id del CV, y es deliberado.</b> El servidor adjunta el
 * CV VIGENTE de quien se presenta, en ese instante. Dejar elegirlo
 * abriría la puerta a presentar el de otra persona —habría que
 * comprobar que el adjunto es tuyo, una comprobación más que se puede
 * olvidar— y a adjuntar una versión antigua que ya se retiró.
 *
 * La carta es opcional: obligar a escribir una para optar a un puesto
 * interno, donde la trayectoria ya se conoce, solo consigue que menos
 * gente se presente.
 */
public record JobApplicationRequest(

        @Size(max = 2000, message = "La carta no puede pasar de 2000 caracteres.")
        String carta
) {
}
