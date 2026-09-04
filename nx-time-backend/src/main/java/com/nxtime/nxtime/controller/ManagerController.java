package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.CreateEmployeeRequest;
import com.nxtime.nxtime.dto.CreateManagerRequest;
import com.nxtime.nxtime.dto.SimpleEmployeeDTO;
import com.nxtime.nxtime.dto.UpdateEmployeeProfileRequest;
import com.nxtime.nxtime.dto.UpdateEmployeeStatusRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.AbsenceService;
import com.nxtime.nxtime.service.AuthService;
import com.nxtime.nxtime.service.EmployeeProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Gestión", description = "Alta de empleados/gestores, listado del equipo y baja de empleados. "
        + "Authorities distintas según GESTOR/RRHH/ADMIN, ver RoleAuthorities.")
@SecurityRequirement(name = "bearerAuth")
public class ManagerController {

    private final AuthService authService;
    private final AbsenceService absenceService;
    private final EmployeeProfileService employeeProfileService;

    public ManagerController(AuthService authService,
                             AbsenceService absenceService,
                             EmployeeProfileService employeeProfileService) {
        this.authService = authService;
        this.absenceService = absenceService;
        this.employeeProfileService = employeeProfileService;
    }

    @Operation(summary = "Crear un empleado", description = "En la empresa de quien lo crea. Rol EMPLEADO fijo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empleado creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empleado:crear'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El email ya está registrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/empleados")
    @PreAuthorize("hasAuthority('empleado:crear')")
    public ResponseEntity<Void> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request, @AuthenticationPrincipal SecurityUser manager) {
        authService.createEmployee(request, manager.getUser());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Crear un gestor", description = "Solo ADMIN: antes cualquier GESTOR podía crear otro sin límite.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gestor creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'gestor:crear' (solo ADMIN la tiene)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El email ya está registrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/gestores")
    @PreAuthorize("hasAuthority('gestor:crear')")
    public ResponseEntity<Void> createManager(
            @Valid @RequestBody CreateManagerRequest request, @AuthenticationPrincipal SecurityUser manager) {
        authService.createManager(request, manager.getUser());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Listar mis empleados", description = "Los EMPLEADO de la empresa de quien pregunta, "
            + "con su jornada semanal y sus días de vacaciones efectivos del año en curso.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de empleados",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SimpleEmployeeDTO.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empleado:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/mis-empleados")
    @PreAuthorize("hasAuthority('empleado:leer')")
    public ResponseEntity<List<SimpleEmployeeDTO>> getMyEmployees(@AuthenticationPrincipal SecurityUser manager) {
        return ResponseEntity.ok(employeeProfileService.getMyEmployees(manager.getUser()));
    }

    @Operation(summary = "Historial de ausencias del equipo", description = "Todas las que ya no están PENDIENTE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial de ausencias",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AbsenceResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'ausencia:leer:equipo'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('ausencia:leer:equipo')")
    @GetMapping("/ausencias-historial")
    public ResponseEntity<List<AbsenceResponse>> getAbsenceHistory(Authentication authentication) {
        return ResponseEntity.ok(absenceService.getHistory(authentication.getName()));
    }

    // Nuevo en la Fase 4: antes no había forma de dar de baja a un
    // empleado (ver auditoría, defectos de diseño) -- los flags de
    // cuenta estaban cableados a "activo" sin excepción.
    @Operation(summary = "Activar o dar de baja a un empleado",
            description = "Un empleado dado de baja no puede autenticarse, pero sus fichajes/ausencias pasadas "
                    + "se conservan (requisito legal, no se borran).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado"),
            @ApiResponse(responseCode = "400", description = "Falta el campo 'activo'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin authority, o empleado de otra empresa (aislamiento multi-tenant)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
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

    // Nuevo en la Fase A: usuarios.horas_semanales y saldo_vacaciones
    // existían desde la Fase 9 pero SOLO se leían -- no había ningún
    // endpoint que los escribiera, así que toda la plantilla se
    // quedaba en 40 h/semana y en los 22 días por defecto para siempre.
    @Operation(summary = "Configurar la ficha de un empleado",
            description = "Jornada semanal en horas y días de vacaciones del AÑO EN CURSO (Europe/Madrid). "
                    + "Es un PATCH: los campos ausentes o null NO se tocan, y un cuerpo vacío es un 200 sin "
                    + "efecto. Si el empleado todavía no tenía saldo de vacaciones para el año, se crea.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ficha actualizada",
                    content = @Content(schema = @Schema(implementation = SimpleEmployeeDTO.class))),
            @ApiResponse(responseCode = "400", description = "Jornada fuera de (0, 60] o días negativos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empleado:gestionar', "
                    + "o empleado de otra empresa (aislamiento multi-tenant)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/empleados/{id}/ficha")
    @PreAuthorize("hasAuthority('empleado:gestionar')")
    public ResponseEntity<SimpleEmployeeDTO> updateEmployeeProfile(
            @PathVariable long id,
            @Valid @RequestBody UpdateEmployeeProfileRequest request,
            @AuthenticationPrincipal SecurityUser manager
    ) {
        return ResponseEntity.ok(employeeProfileService.updateProfile(id, request, manager.getUser()));
    }
}
