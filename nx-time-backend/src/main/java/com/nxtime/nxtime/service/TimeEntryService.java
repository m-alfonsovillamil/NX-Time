package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryCorrectionRequest;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import java.util.List;
import java.util.Optional;

public interface TimeEntryService {

    TimeEntry registerTimeEntry(String userEmail, TimeEntryRequest request);

    Optional<TimeEntry> getActiveTimeEntry(String userEmail);

    List<TimeEntry> getHistory(String userEmail);

    List<TeamTimeEntryDTO> getTeamHistory(String managerEmail);

    /**
     * Corrige un fichaje ya cerrado (Fase 8): nunca sobrescribe la fila
     * original, la anula y crea una nueva con los valores correctos.
     * Restringido a RRHH/ADMIN de la misma empresa que el fichaje.
     */
    TimeEntry correctTimeEntry(String actorEmail, long timeEntryId, TimeEntryCorrectionRequest request);

    /** Línea temporal completa de cambios de un fichaje. Mismo control de empresa que correctTimeEntry. */
    List<TimeEntryAudit> getAuditTrail(String actorEmail, long timeEntryId);
}
