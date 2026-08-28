package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.service.WorkingDayService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cálculo de días hábiles del calendario laboral (Fase 9).
 *
 * "Hábil" = de lunes a viernes y no festivo. Los festivos salen de la
 * tabla "festivos": los propios de la empresa más los nacionales (ver
 * {@link Holiday} y {@link HolidayRepository#findAplicables}).
 *
 * Se consultan TODOS los festivos del rango de una sola vez y se
 * vuelcan a un Set, en lugar de preguntar a la base de datos día a día
 * dentro del bucle: un rango de vacaciones de tres semanas serían 21
 * consultas en vez de una.
 */
@Service
@Transactional(readOnly = true)
public class WorkingDayServiceImpl implements WorkingDayService {

    private final HolidayRepository holidayRepository;

    public WorkingDayServiceImpl(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @Override
    public int contarDiasHabiles(Company empresa, LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            return 0;
        }

        Set<LocalDate> festivos = holidayRepository.findAplicables(empresa.getId(), desde, hasta).stream()
                .map(Holiday::getFecha)
                .collect(Collectors.toSet());

        int habiles = 0;
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            if (esDiaHabil(fecha, festivos)) {
                habiles++;
            }
        }
        return habiles;
    }

    private boolean esDiaHabil(LocalDate fecha, Set<LocalDate> festivos) {
        DayOfWeek dia = fecha.getDayOfWeek();
        boolean finDeSemana = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
        return !finDeSemana && !festivos.contains(fecha);
    }
}
