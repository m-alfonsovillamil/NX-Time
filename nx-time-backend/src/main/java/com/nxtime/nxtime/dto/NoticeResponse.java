package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.NoticeType;
import java.time.Instant;

/**
 * Un aviso tal y como lo ve su destinatario.
 *
 * Sin {@code destinatarioId} ni {@code empresaId}: los endpoints de
 * avisos solo devuelven los de quien pregunta, así que ambos campos
 * serían siempre los suyos y solo añadirían ruido.
 *
 * @param rutaDestino destino LÓGICO que el cliente traduce a su propio
 *                    grafo de navegación. Null cuando el aviso solo
 *                    informa y no lleva a ninguna parte.
 */
public record NoticeResponse(
        long id,
        NoticeType tipo,
        String titulo,
        String cuerpo,
        String rutaDestino,
        boolean leido,
        Instant creadoEn
) {
}
