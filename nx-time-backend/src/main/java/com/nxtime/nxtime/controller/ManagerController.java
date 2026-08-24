package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.CreateEmployeeRequest;
import com.nxtime.nxtime.dto.CreateManagerRequest;
import com.nxtime.nxtime.dto.SimpleEmployeeDTO;
import com.nxtime.nxtime.service.AbsenceService;
import com.nxtime.nxtime.service.AuthService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador exclusivo para operaciones administrativas.
 */
@RestController
@RequestMapping("/api/v1/gestor")
public class ManagerController {

    private final AuthService authService;
    private final AbsenceService absenceService;

    public ManagerController(AuthService authService, AbsenceService absenceService) {
        this.authService = authService;
        this.absenceService = absenceService;
    }

    @PostMapping("/empleados")
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<Void> createEmployee(@RequestBody CreateEmployeeRequest request, @AuthenticationPrincipal User manager) {
        authService.createEmployee(request, manager);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/gestores")
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<Void> createManager(@RequestBody CreateManagerRequest request, @AuthenticationPrincipal User manager) {
        authService.createManager(request, manager);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/mis-empleados")
    @PreAuthorize("hasRole('GESTOR')")
    public ResponseEntity<List<SimpleEmployeeDTO>> getMyEmployees(@AuthenticationPrincipal User manager) {
        return ResponseEntity.ok(authService.getMyEmployees(manager));
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/ausencias-historial")
    public ResponseEntity<List<AbsenceResponse>> getAbsenceHistory(Authentication authentication) {
        return ResponseEntity.ok(absenceService.getHistory(authentication.getName()));
    }
}
