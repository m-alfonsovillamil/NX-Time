package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.JobPostingStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una oferta interna tal como la ve quien la lee (Fase H).
 *
 * Tres campos viajan <b>resueltos por el servidor</b> en vez de dejar
 * que el cliente los deduzca, y los tres por el mismo motivo — que la
 * app no reimplemente una regla que ya vive aquí:
 *
 * <ul>
 *   <li>{@code admiteCandidaturas}: publicada Y en plazo. Son dos
 *       condiciones, y una app que solo mirara el estado ofrecería
 *       presentarse a una oferta cuyo plazo venció.</li>
 *   <li>{@code yaMePresente}: si quien pregunta tiene candidatura. Es lo
 *       que convierte el botón en "Ver mi candidatura".</li>
 *   <li>{@code plazoVencido}: para poder decir <i>por qué</i> no se
 *       admite, que no es lo mismo que no admitir.</li>
 * </ul>
 *
 * {@code candidaturas} solo llega a quien puede valorarlas: cuántas
 * personas de la casa han optado a un puesto no es dato para el resto de
 * la plantilla.
 */
public record JobPostingResponse(
        long id,
        String titulo,
        String descripcion,
        String puesto,
        String departamento,
        String publicadaPor,
        JobPostingStatus estado,
        Instant fechaPublicacion,
        LocalDate fechaCierre,

        /** Publicada y en plazo. */
        boolean admiteCandidaturas,

        /** Tenía fecha de cierre y ya pasó. */
        boolean plazoVencido,

        /** Si quien pregunta ya presentó su candidatura. */
        boolean yaMePresente,

        /** Cuántas hay. Null para quien no puede valorarlas. */
        Long candidaturas
) {
}
