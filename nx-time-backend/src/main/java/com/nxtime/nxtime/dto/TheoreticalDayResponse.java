package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.service.JornadaTeoricaService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * El horario teórico de un día, tal como lo calcula JornadaTeoricaService.
 *
 * @param origen de dónde sale: NO_LABORABLE (festivo o ausencia aprobada),
 *   EXCEPCION (un día distinto), CUADRANTE (la plantilla) o SIN_CUADRANTE.
 * @param entrada la hora a la que debía entrar, si trabaja ese día.
 */
public record TheoreticalDayResponse(
        LocalDate fecha,
        JornadaTeoricaService.Origen origen,
        int minutos,
        @Schema(nullable = true, example = "09:00") String entrada,
        List<ScheduleTemplateResponse.Tramo> tramos,
        @Schema(nullable = true) String motivo,
        @Schema(nullable = true) String plantilla
) {
}
