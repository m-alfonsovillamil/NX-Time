package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryCorrectionRequest;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.dto.TimeEntryResponse;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.service.TimeEntryService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@Tag(name = "Fichaje", description = "Registro horario: iniciar/finalizar jornada, pausas e historial.")
@SecurityRequirement(name = "bearerAuth")
public class TimeEntryController {

    private final TimeEntryService timeEntryService;
    private final TimeEntryMapper timeEntryMapper;

    public TimeEntryController(TimeEntryService timeEntryService, TimeEntryMapper timeEntryMapper) {
        this.timeEntryService = timeEntryService;
        this.timeEntryMapper = timeEntryMapper;
    }

    @Operation(summary = "Fichar (INICIO/FIN/PAUSA_INICIO/PAUSA_FIN)",
            description = "Avanza la máquina de estados del fichaje del usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichaje registrado",
                    content = @Content(schema = @Schema(implementation = TimeEntryResponse.class))),
            @ApiResponse(responseCode = "400", description = "'tipo' ausente o inválido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:escribir'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Transición inválida "
                    + "(ej. iniciar con una jornada ya activa, pausar sin jornada...)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:escribir')")
    @PostMapping
    public ResponseEntity<TimeEntryResponse> registerTimeEntry(
            @Valid @RequestBody TimeEntryRequest request, Authentication authentication) {
        var entry = timeEntryService.registerTimeEntry(authentication.getName(), request);
        return ResponseEntity.ok(timeEntryMapper.toResponse(entry));
    }

    @Operation(summary = "Consultar la jornada activa", description = "204 si no hay ninguna jornada abierta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jornada activa",
                    content = @Content(schema = @Schema(implementation = TimeEntryResponse.class))),
            @ApiResponse(responseCode = "204", description = "No hay jornada activa"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer')")
    @GetMapping("/activo")
    public ResponseEntity<TimeEntryResponse> getActiveTimeEntry(Authentication authentication) {
        return timeEntryService.getActiveTimeEntry(authentication.getName())
                .map(timeEntryMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Historial de fichajes propio", description = "Los últimos 200, más recientes primero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TimeEntryResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer')")
    @GetMapping("/historial")
    public ResponseEntity<List<TimeEntryResponse>> getHistory(Authentication authentication) {
        List<TimeEntryResponse> history = timeEntryService.getHistory(authentication.getName())
                .stream().map(timeEntryMapper::toResponse).toList();
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Historial de fichajes del equipo (gestor)",
            description = "Solo los EMPLEADO de la empresa del gestor autenticado, nunca otros gestores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial del equipo",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TeamTimeEntryDTO.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer:equipo'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer:equipo')")
    @GetMapping("/gestor/historial")
    public ResponseEntity<List<TeamTimeEntryDTO>> getTeamHistory(Authentication authentication) {
        return ResponseEntity.ok(timeEntryService.getTeamHistory(authentication.getName()));
    }

    @Operation(summary = "Corregir un fichaje pasado (RRHH/ADMIN)",
            description = "Nunca sobrescribe: anula el fichaje original y crea uno nuevo con los valores "
                    + "corregidos, enlazado al original. Solo sobre fichajes ya cerrados (con horaSalida). "
                    + "Queda una traza completa en /api/v1/auditoria/fichaje/{id} (ver AuditController).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichaje corregido (se devuelve el nuevo, no el original)",
                    content = @Content(schema = @Schema(implementation = TimeEntryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o horaSalida no posterior a horaEntrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:corregir', "
                    + "o fichaje de otra empresa (aislamiento multi-tenant)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Fichaje no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El fichaje está activo (sin cerrar) o ya fue corregido antes",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:corregir')")
    @PatchMapping("/{id}")
    public ResponseEntity<TimeEntryResponse> correctTimeEntry(
            @PathVariable long id, @Valid @RequestBody TimeEntryCorrectionRequest request, Authentication authentication) {
        var corrected = timeEntryService.correctTimeEntry(authentication.getName(), id, request);
        return ResponseEntity.ok(timeEntryMapper.toResponse(corrected));
    }
}
