package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.CorrectionStatus;
import java.time.Instant;

/**
 * Una solicitud de corrección tal y como se enseña.
 *
 * Lleva las horas ACTUALES del fichaje además de las propuestas: quien
 * tiene que aprobar necesita ver las dos para decidir, y pedirle que
 * abra el historial en otra pantalla para comparar convierte una
 * decisión de dos segundos en una navegación.
 *
 * {@code puedoResolver} y {@code puedoDisputar} los calcula el servidor
 * <b>para quien pregunta</b>. No son propiedades de la solicitud sino de
 * la relación entre ella y quien la mira: la misma fila le sale
 * resoluble a una persona y no a otra, porque quién resuelve depende de
 * quién pidió (ver ADR 010). Dejar que el cliente lo dedujera obligaría
 * a replicar esa regla en la app, y las dos copias acabarían discrepando.
 */
public record CorrectionResponse(
        long id,
        long fichajeId,

        /** De quién es el fichaje. No tiene por qué ser el solicitante. */
        SimpleUserDTO empleado,
        SimpleUserDTO solicitante,

        Instant horaEntradaActual,
        Instant horaSalidaActual,
        Instant horaEntradaPropuesta,
        Instant horaSalidaPropuesta,

        String motivo,
        CorrectionStatus estado,

        SimpleUserDTO aprobador,
        Instant fechaResolucion,
        String comentarioResolucion,
        String motivoDisputa,
        Instant creadoEn,

        boolean puedoResolver,
        boolean puedoDisputar
) {
}
