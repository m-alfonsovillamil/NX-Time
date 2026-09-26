package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import java.util.List;
import java.util.Optional;

public interface TimeEntryService {

    TimeEntry registerTimeEntry(String userEmail, TimeEntryRequest request);

    Optional<TimeEntry> getActiveTimeEntry(String userEmail);

    /** El historial propio, más reciente primero, por páginas (Fase A7). */
    org.springframework.data.domain.Page<TimeEntry> getHistory(
            String userEmail, org.springframework.data.domain.Pageable pagina);

    /** Los proyectos en los que puede fichar hoy y el de la jornada en curso (ADR 017). */
    com.nxtime.nxtime.dto.ClockProjectsResponse proyectosParaFichar(String userEmail);

    /**
     * Cambia de proyecto con la jornada abierta. 409 si está cerrada, en pausa
     * o ya en ese proyecto; 403 si no es suya o no tiene ese proyecto hoy.
     */
    com.nxtime.nxtime.dto.ClockProjectsResponse cambiarProyecto(String userEmail, long registroId, long proyectoId);

    /** Si hoy (en España) no es laborable para esta persona, por qué. Ver NonWorkingDayService. */
    java.util.Optional<com.nxtime.nxtime.service.NonWorkingDayService.Motivo> motivoNoLaborableHoy(String userEmail);

    /**
     * El historial propio entre dos días de España, los dos incluidos. 400 si
     * {@code desde} es posterior a {@code hasta} o el periodo pasa de un año.
     */
    org.springframework.data.domain.Page<TimeEntry> getHistory(
            String userEmail, java.time.LocalDate desde, java.time.LocalDate hasta,
            org.springframework.data.domain.Pageable pagina);

    /** El historial de los EMPLEADO de la empresa, por páginas (Fase A7). */
    org.springframework.data.domain.Page<TeamTimeEntryDTO> getTeamHistory(
            String managerEmail, org.springframework.data.domain.Pageable pagina);

        /** Línea temporal completa de cambios de un fichaje. Mismo control de empresa que el resto. */
    List<TimeEntryAudit> getAuditTrail(String actorEmail, long timeEntryId);
}
