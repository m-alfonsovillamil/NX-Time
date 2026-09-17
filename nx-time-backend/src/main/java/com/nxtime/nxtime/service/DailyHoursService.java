package com.nxtime.nxtime.service;

import com.nxtime.nxtime.dto.DailyHoursResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Horas propias día a día, para los gráficos de la pantalla de inicio.
 *
 * Aparte de {@link DashboardService} a propósito: el resumen se cachea por
 * día y se pide en cada arranque, y esto se pide solo al abrir un gráfico.
 */
public interface DailyHoursService {

    /** Un elemento por día, de {@code desde} a {@code hasta} incluidos. 400 si va al revés o pasa de 62 días. */
    List<DailyHoursResponse> horasPorDia(String email, LocalDate desde, LocalDate hasta);
}
