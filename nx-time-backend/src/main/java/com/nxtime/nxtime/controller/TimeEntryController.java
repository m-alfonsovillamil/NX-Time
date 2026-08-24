package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.service.TimeEntryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para gestionar el registro horario.
 *
 * Sigue devolviendo la entidad JPA TimeEntry directamente (con el
 * usuario -- y su hash de contraseña -- anidado dentro). Es el defecto
 * #1 de la auditoría; se corrige en la Fase 2, no aquí.
 */
@RestController
@RequestMapping("/api/v1/fichaje")
public class TimeEntryController {

    private final TimeEntryService timeEntryService;

    public TimeEntryController(TimeEntryService timeEntryService) {
        this.timeEntryService = timeEntryService;
    }

    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @PostMapping
    public ResponseEntity<TimeEntry> registerTimeEntry(@RequestBody TimeEntryRequest request, Authentication authentication) {
        TimeEntry entry = timeEntryService.registerTimeEntry(authentication.getName(), request);
        return ResponseEntity.ok(entry);
    }

    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @GetMapping("/activo")
    public ResponseEntity<TimeEntry> getActiveTimeEntry(Authentication authentication) {
        return timeEntryService.getActiveTimeEntry(authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @GetMapping("/historial")
    public ResponseEntity<List<TimeEntry>> getHistory(Authentication authentication) {
        return ResponseEntity.ok(timeEntryService.getHistory(authentication.getName()));
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/gestor/historial")
    public ResponseEntity<List<TeamTimeEntryDTO>> getTeamHistory(Authentication authentication) {
        return ResponseEntity.ok(timeEntryService.getTeamHistory(authentication.getName()));
    }
}
