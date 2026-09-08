package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.ComplaintCategory;
import com.nxtime.nxtime.domain.ComplaintStatus;
import java.time.Instant;

/**
 * Una fila de la bandeja del canal (Fase G).
 *
 * <b>No lleva la descripción</b>, y no es por ahorrar bytes: una lista
 * se mira de refilón, a veces con alguien detrás, y el relato de un
 * acoso no es algo que deba aparecer en una vista de conjunto. Para
 * leerlo hay que abrir el expediente, que es un gesto deliberado.
 *
 * Sí lleva los plazos, porque son lo que ordena el trabajo de quien
 * instruye: la bandeja existe para ver qué se está quedando sin tiempo.
 */
public record ComplaintSummaryResponse(
        long id,
        ComplaintCategory categoria,
        String categoriaEtiqueta,
        ComplaintStatus estado,
        boolean anonima,
        Instant creadoEn,
        Instant acuseReciboEn,
        Long diasHastaAcuse,
        Long diasHastaRespuesta,
        int mensajes
) {
}
