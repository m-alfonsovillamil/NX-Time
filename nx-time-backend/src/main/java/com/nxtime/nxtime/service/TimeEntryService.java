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

    List<TimeEntry> getHistory(String userEmail);

    /** Si hoy (en España) no es laborable para esta persona, por qué. Ver NonWorkingDayService. */
    java.util.Optional<com.nxtime.nxtime.service.NonWorkingDayService.Motivo> motivoNoLaborableHoy(String userEmail);

    /**
     * El historial propio entre dos días de España, los dos incluidos. 400 si
     * {@code desde} es posterior a {@code hasta} o el periodo pasa de un año.
     */
    List<TimeEntry> getHistory(String userEmail, java.time.LocalDate desde, java.time.LocalDate hasta);

    List<TeamTimeEntryDTO> getTeamHistory(String managerEmail);

        /** Línea temporal completa de cambios de un fichaje. Mismo control de empresa que el resto. */
    List<TimeEntryAudit> getAuditTrail(String actorEmail, long timeEntryId);
}
