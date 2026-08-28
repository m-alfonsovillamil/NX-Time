package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.Company;
import java.time.LocalDate;

public interface WorkingDayService {

    /**
     * Días hábiles entre dos fechas, ambas incluidas: descuenta fines de
     * semana y los festivos aplicables a esa empresa (Fase 9). Sin esto,
     * "5 días de vacaciones" contaba también sábados y domingos.
     */
    int contarDiasHabiles(Company empresa, LocalDate desde, LocalDate hasta);
}
