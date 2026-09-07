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

    List<TeamTimeEntryDTO> getTeamHistory(String managerEmail);

        /** Línea temporal completa de cambios de un fichaje. Mismo control de empresa que el resto. */
    List<TimeEntryAudit> getAuditTrail(String actorEmail, long timeEntryId);
}
