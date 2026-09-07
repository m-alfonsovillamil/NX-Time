package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.domain.CorrectionStatus;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.dto.TimeEntryResponse;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.CorrectionService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final CorrectionService correctionService;

    public TimeEntryController(
            TimeEntryService timeEntryService,
            TimeEntryMapper timeEntryMapper,
            CorrectionService correctionService) {
        this.timeEntryService = timeEntryService;
        this.timeEntryMapper = timeEntryMapper;
        this.correctionService = correctionService;
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

    @Operation(summary = "Pedir que se corrija un fichaje pasado",
            description = """
                    Sustituye al PATCH /api/v1/fichaje/{id} de antes, que corregía EN EL ACTO.

                    Ahora **crea una solicitud** y el fichaje no se toca hasta que alguien la                     aprueba. Quién aprueba depende de quién pide: si la pides sobre tu propio                     fichaje la aprueba alguien con 'correccion:aprobar'; si la pides sobre el                     fichaje de otra persona (necesitas 'fichaje:corregir') la aprueba ELLA, que                     también puede disputarla.

                    Se aplica en el acto en un solo caso: que sea tu fichaje y tengas                     'correccion:aprobar'. Sin esa excepción, un ADMIN no podría corregir nunca                     su propio fichaje. La auditoría lo deja anotado como auto-aprobación.

                    Devuelve 202 si queda pendiente y 200 si se ha aplicado: el estado de la                     respuesta dice cuál de los dos ha pasado.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aplicada en el acto (auto-aprobación)",
                    content = @Content(schema = @Schema(implementation = CorrectionResponse.class))),
            @ApiResponse(responseCode = "202", description = "Solicitud creada, pendiente de aprobación",
                    content = @Content(schema = @Schema(implementation = CorrectionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o horaSalida no posterior a horaEntrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Fichaje de otro sin 'fichaje:corregir', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Fichaje no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Jornada sin cerrar, ya corregida, o con otra solicitud viva",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('correccion:solicitar')")
    @PostMapping("/{id}/correcciones")
    public ResponseEntity<CorrectionResponse> solicitarCorreccion(
            @PathVariable long id,
            @Valid @RequestBody CorrectionRequestDTO request,
            @AuthenticationPrincipal SecurityUser usuario) {
        CorrectionResponse solicitud = correctionService.solicitar(id, request, usuario.getUser());
        // 202 = "aceptado, pero todavía no ha pasado nada con el fichaje".
        // Es la diferencia que importa para quien llama: con 200 el
        // fichaje YA cambió.
        HttpStatus estado = solicitud.estado() == CorrectionStatus.APROBADA
                ? HttpStatus.OK
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(estado).body(solicitud);
    }
}
