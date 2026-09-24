package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.JustifyIncidentRequest;
import com.nxtime.nxtime.dto.ResolveIncidentRequest;
import com.nxtime.nxtime.dto.ScheduleIncidentResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.ScheduleIncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incidencias de cuadrante (Fase B2).
 *
 * Ver y explicar las propias es {@code cuadrante:leer}, como ver el cuadrante:
 * son tuyas. La bandeja del equipo y decidir es
 * {@code cuadrante:incidencias:revisar}, desde GESTOR, y ni con ella se decide
 * sobre las propias (lo corta el servicio).
 */
@RestController
@RequestMapping("/api/v1/incidencias")
@Tag(name = "Incidencias de cuadrante", description = "Retrasos, salidas anticipadas y ausencias contra el "
        + "cuadrante. Se detectan, no se imputan: ninguna descuenta nada.")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleIncidentController {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final ScheduleIncidentService incidentService;

    public ScheduleIncidentController(ScheduleIncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @Operation(summary = "Mis incidencias de un año", description = "Por defecto, el año en curso.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incidencias",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleIncidentResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/mias")
    @PreAuthorize("hasAuthority('cuadrante:leer')")
    public ResponseEntity<List<ScheduleIncidentResponse>> mias(
            @RequestParam(required = false) Integer anio,
            @AuthenticationPrincipal SecurityUser usuario) {
        int elAnio = anio != null ? anio : LocalDate.now(MADRID).getYear();
        return ResponseEntity.ok(incidentService.mias(usuario.getUser(), elAnio));
    }

    @Operation(summary = "La bandeja del equipo",
            description = "Las de la empresa sin las propias. Por defecto las que esperan decisión "
                    + "(PENDIENTE y JUSTIFICADA); con resueltas=true, las ya decididas. Como mucho 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incidencias",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleIncidentResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:incidencias:revisar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/equipo")
    @PreAuthorize("hasAuthority('cuadrante:incidencias:revisar')")
    public ResponseEntity<List<ScheduleIncidentResponse>> bandeja(
            @RequestParam(defaultValue = "false") boolean resueltas,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(incidentService.bandeja(usuario.getUser(), resueltas));
    }

    @Operation(summary = "Explicar una incidencia propia",
            description = "Pasa a JUSTIFICADA. Se puede rehacer mientras nadie haya decidido.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Explicada",
                    content = @Content(schema = @Schema(implementation = ScheduleIncidentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Explicación vacía o demasiado larga",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "No es tuya, o es de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya está decidida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/justificacion")
    @PreAuthorize("hasAuthority('cuadrante:leer')")
    public ResponseEntity<ScheduleIncidentResponse> justificar(
            @PathVariable long id,
            @Valid @RequestBody JustifyIncidentRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(incidentService.justificar(id, request.texto(), usuario.getUser()));
    }

    @Operation(summary = "Decidir sobre una incidencia",
            description = "ACEPTADA o RECHAZADA. Aceptar no pide comentario; rechazar, sí. Nadie decide "
                    + "sobre las suyas, tenga el permiso que tenga.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Decidida",
                    content = @Content(schema = @Schema(implementation = ScheduleIncidentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Rechazar sin comentario",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin el permiso, es tuya, o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya está decidida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/resolucion")
    @PreAuthorize("hasAuthority('cuadrante:incidencias:revisar')")
    public ResponseEntity<ScheduleIncidentResponse> resolver(
            @PathVariable long id,
            @Valid @RequestBody ResolveIncidentRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(incidentService.resolver(
                id, Boolean.TRUE.equals(request.aceptar()), request.comentario(), usuario.getUser()));
    }
}
