package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.AbsenceRequestDTO;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.UpdateAbsenceStatusRequest;
import com.nxtime.nxtime.dto.VacationBalanceResponse;
import com.nxtime.nxtime.service.AbsenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST que gestiona las operaciones de vacaciones y bajas.
 */
@RestController
@RequestMapping("/api/v1/ausencias")
@Tag(name = "Ausencias", description = "Solicitud de ausencias (vacaciones, bajas...) y su aprobación/rechazo por un gestor.")
@SecurityRequirement(name = "bearerAuth")
public class AbsenceController {

    /** "El año actual" es el año en España, no en UTC (ver TimeEntryMapper). */
    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    private final AbsenceService absenceService;

    public AbsenceController(AbsenceService absenceService) {
        this.absenceService = absenceService;
    }

    @Operation(summary = "Solicitar una ausencia", description = "Se crea en estado PENDIENTE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Petición creada",
                    content = @Content(schema = @Schema(implementation = AbsenceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o fechaInicio posterior a fechaFin",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'ausencia:escribir'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('ausencia:escribir')")
    @PostMapping
    public ResponseEntity<AbsenceResponse> requestAbsence(
            @Valid @RequestBody AbsenceRequestDTO requestDTO, Authentication authentication) {
        return ResponseEntity.ok(absenceService.createRequest(authentication.getName(), requestDTO));
    }

    @Operation(summary = "Mis peticiones de ausencia", description = "Todas, en cualquier estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peticiones del usuario",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AbsenceResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'ausencia:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('ausencia:leer')")
    @GetMapping("/mis-peticiones")
    public ResponseEntity<List<AbsenceResponse>> getMyRequests(Authentication authentication) {
        return ResponseEntity.ok(absenceService.getMyRequests(authentication.getName()));
    }

    @Operation(summary = "Peticiones pendientes del equipo (gestor)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Peticiones PENDIENTE de la empresa del gestor",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AbsenceResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'ausencia:aprobar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('ausencia:aprobar')")
    @GetMapping("/gestor/pendientes")
    public ResponseEntity<List<AbsenceResponse>> getPendingRequests(Authentication authentication) {
        return ResponseEntity.ok(absenceService.getPendingRequests(authentication.getName()));
    }

    // Fase 9: un único PATCH sustituye a los dos POST anteriores
    // (/gestor/aprobar/{id} y /gestor/rechazar/{id}). Cambiar el estado
    // de un recurso que ya existe es semánticamente un PATCH, no dos
    // POST distintos con la acción metida en la URL (ver plan).
    @Operation(summary = "Aprobar o rechazar una petición de ausencia",
            description = "Solo si está PENDIENTE y es de la empresa del gestor. Deja constancia de quién "
                    + "resolvió y cuándo. Al RECHAZAR, el comentario es obligatorio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Petición resuelta",
                    content = @Content(schema = @Schema(implementation = AbsenceResponse.class))),
            @ApiResponse(responseCode = "400", description = "Estado ausente/inválido, o rechazo sin comentario",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin authority, o petición de otra empresa (aislamiento multi-tenant)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Petición no encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "La petición ya no está PENDIENTE",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('ausencia:aprobar')")
    @PatchMapping("/{id}/estado")
    public ResponseEntity<AbsenceResponse> updateRequestStatus(
            @PathVariable long id,
            @Valid @RequestBody UpdateAbsenceStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(absenceService.changeRequestStatus(authentication.getName(), id, request));
    }

    @Operation(summary = "Mi saldo de vacaciones",
            description = "Días de vacaciones a los que se tiene derecho ese año, consumidos y disponibles. "
                    + "Los consumidos se calculan sobre las peticiones ya APROBADAS, en días hábiles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo del año indicado",
                    content = @Content(schema = @Schema(implementation = VacationBalanceResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'ausencia:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('ausencia:leer')")
    @GetMapping("/saldo-vacaciones")
    public ResponseEntity<VacationBalanceResponse> getMyVacationBalance(
            @Parameter(description = "Año a consultar. Por defecto, el actual.")
            @RequestParam(required = false) Integer anio,
            Authentication authentication) {
        int anioConsultado = (anio != null) ? anio : Year.now(MADRID_ZONE).getValue();
        return ResponseEntity.ok(absenceService.getMyVacationBalance(authentication.getName(), anioConsultado));
    }
}
