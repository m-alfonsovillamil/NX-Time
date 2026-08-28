package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.TimeEntryAuditResponse;
import com.nxtime.nxtime.mapper.TimeEntryAuditMapper;
import com.nxtime.nxtime.service.TimeEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la auditoría inalterable de fichajes (Fase 8 del plan de
 * profesionalización). Ver {@link com.nxtime.nxtime.domain.TimeEntryAudit}
 * y {@link com.nxtime.nxtime.audit.TimeEntryAuditListener} para cómo se
 * escribe -- este controlador solo lee.
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@Tag(name = "Auditoría", description = "Línea temporal de cambios de un fichaje (RD-ley 8/2019). Solo lectura.")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final TimeEntryService timeEntryService;
    private final TimeEntryAuditMapper auditMapper;

    public AuditController(TimeEntryService timeEntryService, TimeEntryAuditMapper auditMapper) {
        this.timeEntryService = timeEntryService;
        this.auditMapper = auditMapper;
    }

    @Operation(summary = "Línea temporal de un fichaje",
            description = "Todas las entradas de auditoría de ese fichaje, más antigua primero: su creación, "
                    + "sus modificaciones (fin de jornada, pausas) y, si la hubo, su corrección.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Línea temporal (vacía si el fichaje no tiene cambios registrados)",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TimeEntryAuditResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:auditoria', "
                    + "o fichaje de otra empresa (aislamiento multi-tenant)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Fichaje no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:auditoria')")
    @GetMapping("/fichaje/{id}")
    public ResponseEntity<List<TimeEntryAuditResponse>> getAuditTrail(@PathVariable long id, Authentication authentication) {
        var trail = timeEntryService.getAuditTrail(authentication.getName(), id).stream()
                .map(auditMapper::toResponse)
                .toList();
        return ResponseEntity.ok(trail);
    }
}
