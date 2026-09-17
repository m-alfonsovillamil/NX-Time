package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Si un día NO es de trabajo para una persona, y por qué.
 *
 * Una sola definición, que usan tanto la pregunta de la app antes de iniciar
 * la jornada ({@code GET /fichaje/hoy}) como el aviso a los gestores al
 * iniciarla: si fueran dos reglas, la app podría no preguntar y el gestor
 * recibir igual el aviso, o al revés.
 *
 * <p>Cuenta como no laborable:
 * <ul>
 *   <li>un <b>festivo</b> de la empresa (nacional o propio);</li>
 *   <li>una <b>ausencia APROBADA</b> que cubre el día. Una pendiente no: puede
 *       acabar rechazada, y avisar por ella sería ruido.</li>
 * </ul>
 * Los fines de semana <b>no</b>: hay empresas que trabajan sábados y hoy no
 * existe un calendario laboral por persona.
 */
public interface NonWorkingDayService {

    /**
     * @param texto para leer: "Festivo: Día de la Hispanidad", "Vacaciones".
     * @param vacaciones si el motivo es una ausencia de vacaciones: el aviso
     *     al gestor lo dice, porque puede querer devolver ese día al saldo.
     */
    record Motivo(String texto, boolean vacaciones) {
    }

    Optional<Motivo> motivo(User persona, LocalDate dia);
}
