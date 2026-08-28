package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.service.HolidayCalendar;
import com.nxtime.nxtime.service.WorkingDayService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cálculo de días hábiles del calendario laboral (Fase 9).
 *
 * "Hábil" = de lunes a viernes y no festivo. Los festivos salen de la
 * tabla "festivos": los propios de la empresa más los nacionales (ver
 * {@link Holiday}).
 *
 * Desde la Fase 10 los festivos se piden a {@link HolidayCalendar}, que
 * los cachea por (empresa, año). Antes se consultaban por rango en cada
 * llamada, y montar un listado de ausencias disparaba una consulta por
 * petición -- el N+1 sobre "festivos" que quedó anotado en la Fase 9.
 * Se piden por AÑO y no por el rango exacto porque el año es una clave
 * de caché estable y reutilizable entre llamadas distintas.
 */
@Service
@Transactional(readOnly = true)
public class WorkingDayServiceImpl implements WorkingDayService {

    private final HolidayCalendar holidayCalendar;

    public WorkingDayServiceImpl(HolidayCalendar holidayCalendar) {
        this.holidayCalendar = holidayCalendar;
    }

    @Override
    public int contarDiasHabiles(Company empresa, LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            return 0;
        }

        Set<LocalDate> festivos = festivosDelRango(empresa.getId(), desde, hasta);

        int habiles = 0;
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            if (esDiaHabil(fecha, festivos)) {
                habiles++;
            }
        }
        return habiles;
    }

    /**
     * Un rango puede cruzar el fin de año (del 28/12 al 4/1), así que se
     * unen los calendarios de todos los años que toca. Normalmente es
     * uno solo y no se copia nada de más.
     */
    private Set<LocalDate> festivosDelRango(long empresaId, LocalDate desde, LocalDate hasta) {
        if (desde.getYear() == hasta.getYear()) {
            return holidayCalendar.festivosDelAnio(empresaId, desde.getYear());
        }

        Set<LocalDate> festivos = new HashSet<>();
        for (int anio = desde.getYear(); anio <= hasta.getYear(); anio++) {
            festivos.addAll(holidayCalendar.festivosDelAnio(empresaId, anio));
        }
        return festivos;
    }

    private boolean esDiaHabil(LocalDate fecha, Set<LocalDate> festivos) {
        DayOfWeek dia = fecha.getDayOfWeek();
        boolean finDeSemana = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
        return !finDeSemana && !festivos.contains(fecha);
    }
}
