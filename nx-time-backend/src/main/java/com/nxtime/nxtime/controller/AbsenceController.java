package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.dto.AbsenceRequestDTO;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.service.AbsenceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST que gestiona las operaciones de vacaciones y bajas.
 */
@RestController
@RequestMapping("/api/v1/ausencias")
public class AbsenceController {

    private final AbsenceService absenceService;

    public AbsenceController(AbsenceService absenceService) {
        this.absenceService = absenceService;
    }

    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @PostMapping
    public ResponseEntity<AbsenceResponse> requestAbsence(
            @Valid @RequestBody AbsenceRequestDTO requestDTO, Authentication authentication) {
        return ResponseEntity.ok(absenceService.createRequest(authentication.getName(), requestDTO));
    }

    @PreAuthorize("hasAnyRole('EMPLEADO', 'GESTOR')")
    @GetMapping("/mis-peticiones")
    public ResponseEntity<List<AbsenceResponse>> getMyRequests(Authentication authentication) {
        return ResponseEntity.ok(absenceService.getMyRequests(authentication.getName()));
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/gestor/pendientes")
    public ResponseEntity<List<AbsenceResponse>> getPendingRequests(Authentication authentication) {
        return ResponseEntity.ok(absenceService.getPendingRequests(authentication.getName()));
    }

    @PreAuthorize("hasRole('GESTOR')")
    @PostMapping("/gestor/aprobar/{id}")
    public ResponseEntity<AbsenceResponse> approveRequest(@PathVariable long id, Authentication authentication) {
        return ResponseEntity.ok(absenceService.changeRequestStatus(authentication.getName(), id, AbsenceStatus.APROBADA));
    }

    @PreAuthorize("hasRole('GESTOR')")
    @PostMapping("/gestor/rechazar/{id}")
    public ResponseEntity<AbsenceResponse> rejectRequest(@PathVariable long id, Authentication authentication) {
        return ResponseEntity.ok(absenceService.changeRequestStatus(authentication.getName(), id, AbsenceStatus.RECHAZADA));
    }
}
