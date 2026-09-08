package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.ApplicationStatus;
import java.time.Instant;

/**
 * Una candidatura (Fase H).
 *
 * La devuelven las dos vistas —la del candidato y la de quien valora— y
 * devuelven lo mismo: lo que se decide sobre alguien no se tramita a sus
 * espaldas. Lo único que cambia es a qué endpoint puede llamar cada uno.
 *
 * {@code cvAdjuntoId} es el id del <b>adjunto congelado</b>, no el del
 * CV actual de la persona: es lo que hay que descargar para ver lo que
 * se presentó. Puede apuntar a un adjunto que ya no está vigente, y esa
 * es justamente la razón de que exista {@code Attachment.vigente}.
 */
public record JobApplicationResponse(
        long id,
        long ofertaId,
        String ofertaTitulo,
        long usuarioId,
        String candidato,
        String carta,
        ApplicationStatus estado,
        String resueltaPor,
        Instant fechaResolucion,
        String comentario,
        Instant creadoEn,

        /** El adjunto que se congeló al presentarse. */
        long cvAdjuntoId,
        String cvNombre,

        /**
         * Si quien pregunta puede moverla de estado. Falso sobre la
         * propia: nadie valora su propia candidatura, tenga el rol que
         * tenga (lo corta el servicio, no un {@code @PreAuthorize}).
         */
        boolean puedoValorar
) {
}
