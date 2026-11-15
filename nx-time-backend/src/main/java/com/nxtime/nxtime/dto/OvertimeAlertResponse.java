package com.nxtime.nxtime.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Un exceso de jornada detectado (Fase F).
 *
 * {@code fecha} es el día si el tipo es DIARIA y el <b>lunes</b> de la
 * semana si es SEMANAL; {@code fechaFin} viaja calculada (el domingo, o
 * el mismo día) para que la pantalla no tenga que saber esa regla ni
 * repetir la aritmética de calendario.
 *
 * {@code registroId} solo llega en los diarios: en un aviso semanal el
 * exceso es de la suma de varias jornadas y no hay un fichaje concreto
 * al que apuntar.
 */
public record OvertimeAlertResponse(
        long id,
        long usuarioId,
        String usuario,
        String tipo,
        LocalDate fecha,
        LocalDate fechaFin,
        int minutosExtra,
        /** Los minutos que se esperaban del periodo: el listón que se pasó. */
        long minutosEsperados,
        Long registroId,
        String estado,
        String justificacion,
        String revisadoPor,
        Instant fechaRevision,
        /* ADR 061: PAGO, DESCANSO, o null (sin aceptar, o aceptadas antes de V58: pagadas). */
        String compensacion,
        /* Si se descansan: hasta cuándo, para que no cuenten en la bolsa. */
        LocalDate descansoAntesDe,
        LocalDate descansoDisfrutadoEn
) {
    public OvertimeAlertResponse(long id, long usuarioId, String usuario, String tipo, LocalDate fecha,
            LocalDate fechaFin, int minutosExtra, long minutosEsperados, Long registroId, String estado,
            String justificacion, String revisadoPor, Instant fechaRevision) {
        this(id, usuarioId, usuario, tipo, fecha, fechaFin, minutosExtra, minutosEsperados, registroId, estado,
                justificacion, revisadoPor, fechaRevision, null, null, null);
    }
}
