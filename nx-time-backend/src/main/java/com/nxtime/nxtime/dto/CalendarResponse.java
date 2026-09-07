package com.nxtime.nxtime.dto;

import java.util.List;

/**
 * Todo lo que hay que pintar en un mes del calendario, en una respuesta.
 *
 * Va junto y no en dos endpoints (festivos por un lado, ausencias por
 * otro) porque la pantalla no puede dibujar nada hasta tener las dos
 * cosas: separarlos obligaría al cliente a coordinar dos peticiones y a
 * decidir qué enseñar mientras solo ha llegado una.
 *
 * {@code incluyeEquipo} confirma si la respuesta trae ausencias de otras
 * personas. No es redundante con lo que pidió el cliente: se puede pedir
 * ver al equipo sin tener {@code ausencia:leer:equipo}, y entonces la
 * respuesta llega igualmente con las propias y este campo a
 * {@code false}. Sin él, un calendario sin compañeros ausentes ese mes
 * sería indistinguible de uno al que le han denegado el permiso.
 */
public record CalendarResponse(
        int anio,
        int mes,
        boolean incluyeEquipo,
        List<HolidayResponse> festivos,
        List<CalendarAbsenceDTO> ausencias
) {
}
