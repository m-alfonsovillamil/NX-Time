package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.CreateEmployeeRequest;
import com.nxtime.nxtime.dto.CreateManagerRequest;
import com.nxtime.nxtime.dto.SimpleEmployeeDTO;
import com.nxtime.nxtime.dto.UpdateEmployeeStatusRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.AbsenceService;
import com.nxtime.nxtime.service.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador exclusivo para operaciones administrativas.
 *
 * @AuthenticationPrincipal ahora resuelve a SecurityUser (adaptador de
 * Spring Security), no directamente a la entidad User: desde la Fase 2
 * User ya no implementa UserDetails (ver auditoría, defectos de
 * diseño). SecurityUser.getUser() da acceso a la entidad de dominio
 * que necesitan los servicios.
 *
 * Desde la Fase 4, cada endpoint exige una authority granular (ver
 * RoleAuthorities) en vez de un rol directamente. "gestor:crear" solo
 * la tiene ADMIN: antes cualquier GESTOR podía crear otro GESTOR sin
 * límite (ver auditoría, defectos de diseño).
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
    @PreAuthorize("hasAuthority('empleado:crear')")
    public ResponseEntity<Void> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request, @AuthenticationPrincipal SecurityUser manager) {
        authService.createEmployee(request, manager.getUser());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/gestores")
    @PreAuthorize("hasAuthority('gestor:crear')")
    public ResponseEntity<Void> createManager(
            @Valid @RequestBody CreateManagerRequest request, @AuthenticationPrincipal SecurityUser manager) {
        authService.createManager(request, manager.getUser());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/mis-empleados")
    @PreAuthorize("hasAuthority('empleado:leer')")
    public ResponseEntity<List<SimpleEmployeeDTO>> getMyEmployees(@AuthenticationPrincipal SecurityUser manager) {
        return ResponseEntity.ok(authService.getMyEmployees(manager.getUser()));
    }

    @PreAuthorize("hasAuthority('ausencia:leer:equipo')")
    @GetMapping("/ausencias-historial")
    public ResponseEntity<List<AbsenceResponse>> getAbsenceHistory(Authentication authentication) {
        return ResponseEntity.ok(absenceService.getHistory(authentication.getName()));
    }

    // Nuevo en la Fase 4: antes no había forma de dar de baja a un
    // empleado (ver auditoría, defectos de diseño) -- los flags de
    // cuenta estaban cableados a "activo" sin excepción.
    @PatchMapping("/empleados/{id}/estado")
    @PreAuthorize("hasAuthority('empleado:gestionar')")
    public ResponseEntity<Void> updateEmployeeStatus(
            @PathVariable long id,
            @Valid @RequestBody UpdateEmployeeStatusRequest request,
            @AuthenticationPrincipal SecurityUser manager
    ) {
        authService.setEmployeeActive(id, request.activo(), manager.getUser());
        return ResponseEntity.ok().build();
    }
}
