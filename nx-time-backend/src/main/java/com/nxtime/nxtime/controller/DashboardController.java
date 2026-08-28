package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.CompanyDashboardResponse;
import com.nxtime.nxtime.dto.PersonalDashboardResponse;
import com.nxtime.nxtime.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Métricas agregadas (Fase 10). Reutiliza las authorities que ya
 * existen -- "fichaje:leer" para lo propio y "fichaje:leer:equipo" para
 * lo de la empresa -- en vez de inventar dos nuevas: el dashboard no
 * enseña nada que esas authorities no permitieran ver ya, solo lo
 * enseña sumado.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Métricas agregadas: horas trabajadas, pendientes e incidencias.")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Mi resumen",
            description = "Minutos trabajados hoy, esta semana y este mes (descontando pausas), estado de "
                    + "fichaje actual, ausencias pendientes y saldo de vacaciones. Los periodos se calculan "
                    + "en hora española, no en UTC.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen del usuario autenticado",
                    content = @Content(schema = @Schema(implementation = PersonalDashboardResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer')")
    @GetMapping("/resumen")
    public ResponseEntity<PersonalDashboardResponse> getPersonalDashboard(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getPersonalDashboard(authentication.getName()));
    }

    @Operation(summary = "Resumen de la empresa (gestor)",
            description = "Agregados del mes en curso para la empresa del gestor: empleados activos, minutos "
                    + "totales, ausencias por aprobar, incidencias de fichaje sin corregir y horas por empleado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen de la empresa",
                    content = @Content(schema = @Schema(implementation = CompanyDashboardResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer:equipo'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer:equipo')")
    @GetMapping("/empresa")
    public ResponseEntity<CompanyDashboardResponse> getCompanyDashboard(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getCompanyDashboard(authentication.getName()));
    }
}
