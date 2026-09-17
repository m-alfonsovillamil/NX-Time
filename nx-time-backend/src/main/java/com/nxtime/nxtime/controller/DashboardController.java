package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.CompanyDashboardResponse;
import com.nxtime.nxtime.dto.DailyHoursResponse;
import com.nxtime.nxtime.dto.PendingWorkResponse;
import com.nxtime.nxtime.dto.PersonalDashboardResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.DailyHoursService;
import com.nxtime.nxtime.service.DashboardService;
import com.nxtime.nxtime.service.PendingWorkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final PendingWorkService pendingWorkService;
    private final DailyHoursService dailyHoursService;

    public DashboardController(
            DashboardService dashboardService,
            PendingWorkService pendingWorkService,
            DailyHoursService dailyHoursService) {
        this.dashboardService = dashboardService;
        this.pendingWorkService = pendingWorkService;
        this.dailyHoursService = dailyHoursService;
    }

    @Operation(summary = "Mis horas día a día",
            description = "Un elemento por día entre 'desde' y 'hasta' (días de España, incluidos, 62 como máximo): "
                    + "minutos netos de las jornadas cerradas que empezaron ese día, minutos esperados según la "
                    + "jornada contratada (0 en fin de semana, festivo o ausencia aprobada), y el nombre del "
                    + "festivo o el tipo de ausencia si los hay. Para los gráficos de la pantalla de inicio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Días",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DailyHoursResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Fechas que faltan o mal escritas, al revés, o más de 62 días",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer')")
    @GetMapping("/horas-por-dia")
    public ResponseEntity<List<DailyHoursResponse>> getHorasPorDia(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta,
            Authentication authentication) {
        return ResponseEntity.ok(dailyHoursService.horasPorDia(authentication.getName(), desde, hasta));
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

    @Operation(summary = "Lo que espera tu decisión (panel de gestión)",
            description = "Los contadores de las tres bandejas: ausencias por aprobar, correcciones que te "
                    + "toca resolver y avisos de horas extra abiertos. Cada número es exactamente lo que "
                    + "verías al abrir esa bandeja, no un total de la empresa. Sin permiso para una "
                    + "bandeja, su contador es 0.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contadores",
                    content = @Content(schema = @Schema(implementation = PendingWorkResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer:equipo'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer:equipo')")
    @GetMapping("/pendientes")
    public ResponseEntity<PendingWorkResponse> getPendingWork(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(pendingWorkService.contar(usuario.getUser()));
    }
}
