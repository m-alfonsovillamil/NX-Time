package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.dto.TimeEntryResponse;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.service.TimeEntryService;
import jakarta.validation.Valid;
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
 * Desde esta fase ya NO devuelve la entidad TimeEntry directamente
 * (ver auditoría, defecto #1: la entidad arrastraba al Usuario, y con
 * él, su contraseña cifrada). Cada endpoint mapea a un DTO explícito.
 */
@RestController
@RequestMapping("/api/v1/fichaje")
public class TimeEntryController {

    private final TimeEntryService timeEntryService;
    private final TimeEntryMapper timeEntryMapper;

    public TimeEntryController(TimeEntryService timeEntryService, TimeEntryMapper timeEntryMapper) {
        this.timeEntryService = timeEntryService;
        this.timeEntryMapper = timeEntryMapper;
    }

    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @PostMapping
    public ResponseEntity<TimeEntryResponse> registerTimeEntry(
            @Valid @RequestBody TimeEntryRequest request, Authentication authentication) {
        var entry = timeEntryService.registerTimeEntry(authentication.getName(), request);
        return ResponseEntity.ok(timeEntryMapper.toResponse(entry));
    }

    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @GetMapping("/activo")
    public ResponseEntity<TimeEntryResponse> getActiveTimeEntry(Authentication authentication) {
        return timeEntryService.getActiveTimeEntry(authentication.getName())
                .map(timeEntryMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @GetMapping("/historial")
    public ResponseEntity<List<TimeEntryResponse>> getHistory(Authentication authentication) {
        List<TimeEntryResponse> history = timeEntryService.getHistory(authentication.getName())
                .stream().map(timeEntryMapper::toResponse).toList();
        return ResponseEntity.ok(history);
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/gestor/historial")
    public ResponseEntity<List<TeamTimeEntryDTO>> getTeamHistory(Authentication authentication) {
        return ResponseEntity.ok(timeEntryService.getTeamHistory(authentication.getName()));
    }
}
