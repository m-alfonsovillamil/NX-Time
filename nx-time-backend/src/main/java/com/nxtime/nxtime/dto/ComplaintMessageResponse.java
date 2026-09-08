package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.ComplaintAuthor;
import java.time.Instant;

/**
 * Un mensaje del expediente (Fase G).
 *
 * {@code autor} es null en los del denunciante cuando la denuncia es
 * anónima, y la pantalla no necesita mirarlo para nada: de qué lado
 * pintar el mensaje lo dice {@code autorRol}. Los del instructor
 * siempre llevan nombre — quien instruye responde de lo que escribe.
 */
public record ComplaintMessageResponse(
        long id,
        ComplaintAuthor autorRol,
        String autor,
        String texto,
        Instant creadoEn
) {
}
