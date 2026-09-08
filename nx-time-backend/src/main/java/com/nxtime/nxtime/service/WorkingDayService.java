package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.Company;
import java.time.LocalDate;
import java.util.Set;

public interface WorkingDayService {

    /**
     * Días hábiles entre dos fechas, ambas incluidas: descuenta fines de
     * semana y los festivos aplicables a esa empresa (Fase 9). Sin esto,
     * "5 días de vacaciones" contaba también sábados y domingos.
     */
    int contarDiasHabiles(Company empresa, LocalDate desde, LocalDate hasta);

    /**
     * Los mismos días, uno a uno en vez de contados (Fase F).
     *
     * Existe porque el umbral semanal de horas extra necesita
     * <b>descontar además las ausencias aprobadas</b>, y para saber si el
     * jueves de baja era hábil hay que poder preguntar por el jueves.
     * Con solo el recuento habría que reconstruir el calendario fuera, y
     * entonces la regla de qué es hábil viviría en dos sitios.
     */
    Set<LocalDate> diasHabiles(Company empresa, LocalDate desde, LocalDate hasta);
}
