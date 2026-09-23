package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.CloseScheduleAssignmentRequest;
import com.nxtime.nxtime.dto.ScheduleAssignmentRequest;
import com.nxtime.nxtime.dto.ScheduleAssignmentResponse;
import com.nxtime.nxtime.dto.ScheduleExceptionRequest;
import com.nxtime.nxtime.dto.ScheduleExceptionResponse;
import com.nxtime.nxtime.dto.ScheduleTemplateRequest;
import com.nxtime.nxtime.dto.ScheduleTemplateResponse;
import com.nxtime.nxtime.dto.TeamScheduleEntryResponse;
import com.nxtime.nxtime.dto.TheoreticalDayResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.ScheduleService;
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
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuadrantes y horario teórico (Fase B1).
 *
 * Ver el horario propio es {@code cuadrante:leer} (todo el mundo: saber a qué
 * hora te toca entrar es parte de tu jornada). Todo lo demás —plantillas,
 * asignaciones, excepciones y el horario de otra persona— es
 * {@code cuadrante:gestionar}, desde GESTOR. El del equipo en un día va con
 * {@code fichaje:leer:equipo}, como el resto de vistas del equipo.
 *
 * Los tramos viajan en minutos desde medianoche (09:00 = 540; un turno de
 * 22:00 a 06:00 es 1320 → 1800), y las respuestas añaden las horas ya
 * formateadas para que ningún cliente tenga que hacer la cuenta.
 */
@RestController
@RequestMapping("/api/v1/cuadrantes")
@Tag(name = "Cuadrantes", description = "Plantillas de horario, asignaciones con vigencia, excepciones "
        + "por día y el horario teórico que resulta.")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    // ------------------------------------------------------------------
    // Horario teórico
    // ------------------------------------------------------------------

    @Operation(summary = "Mi horario teórico, día a día",
            description = "Como mucho 62 días. Cada día dice de dónde sale: NO_LABORABLE (festivo o "
                    + "ausencia aprobada, que mandan sobre el cuadrante), EXCEPCION, CUADRANTE o "
                    + "SIN_CUADRANTE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Días",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TheoreticalDayResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Rango al revés o de más de 62 días",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/mio")
    @PreAuthorize("hasAuthority('cuadrante:leer')")
    public ResponseEntity<List<TheoreticalDayResponse>> mio(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(scheduleService.mio(desde, hasta, usuario.getUser()));
    }

    @Operation(summary = "El horario teórico de otra persona",
            description = "De la misma empresa. Como mucho 62 días.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Días",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TheoreticalDayResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Rango al revés o de más de 62 días",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/usuarios/{usuarioId}")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<List<TheoreticalDayResponse>> deUnaPersona(
            @PathVariable long usuarioId,
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(scheduleService.deUnaPersona(usuarioId, desde, hasta, usuario.getUser()));
    }

    @Operation(summary = "Quién tiene cuadrante un día, y qué le toca",
            description = "Solo las personas activas con un cuadrante vigente ese día.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Personas",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TeamScheduleEntryResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Sin 'fichaje:leer:equipo'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/equipo")
    @PreAuthorize("hasAuthority('fichaje:leer:equipo')")
    public ResponseEntity<List<TeamScheduleEntryResponse>> equipo(
            @RequestParam LocalDate fecha,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(scheduleService.equipo(fecha, usuario.getUser()));
    }

    // ------------------------------------------------------------------
    // Plantillas
    // ------------------------------------------------------------------

    @Operation(summary = "Plantillas de horario de la empresa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plantillas",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleTemplateResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/plantillas")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<List<ScheduleTemplateResponse>> plantillas(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(scheduleService.plantillas(usuario.getUser()));
    }

    @Operation(summary = "Crear una plantilla de horario",
            description = "Los tramos no pueden pisarse, tampoco entre días: un turno del lunes de 22:00 a "
                    + "06:00 choca con un tramo del martes que empiece antes de las 06:00.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Creada",
                    content = @Content(schema = @Schema(implementation = ScheduleTemplateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Tramos inválidos o que se pisan",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya hay una plantilla con ese nombre",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/plantillas")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<ScheduleTemplateResponse> crearPlantilla(
            @Valid @RequestBody ScheduleTemplateRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(scheduleService.crearPlantilla(request, usuario.getUser()));
    }

    @Operation(summary = "Editar una plantilla de horario",
            description = "Renombrarla siempre se puede. Cambiar sus tramos, solo si todavía no se ha "
                    + "aplicado a ningún día pasado: si no, reescribiría su horario teórico. En ese caso "
                    + "se crea otra plantilla y se asigna desde la fecha que sea.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Actualizada",
                    content = @Content(schema = @Schema(implementation = ScheduleTemplateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Tramos inválidos o que se pisan",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Plantilla no encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya aplicada al pasado, o nombre repetido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/plantillas/{plantillaId}")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<ScheduleTemplateResponse> editarPlantilla(
            @PathVariable long plantillaId,
            @Valid @RequestBody ScheduleTemplateRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(scheduleService.editarPlantilla(plantillaId, request, usuario.getUser()));
    }

    @Operation(summary = "Borrar una plantilla de horario",
            description = "Solo si nadie la tiene ni la ha tenido asignada.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Borrada"),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Plantilla no encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Alguien la tiene o la ha tenido asignada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/plantillas/{plantillaId}")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<Void> borrarPlantilla(
            @PathVariable long plantillaId, @AuthenticationPrincipal SecurityUser usuario) {
        scheduleService.borrarPlantilla(plantillaId, usuario.getUser());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Asignaciones
    // ------------------------------------------------------------------

    @Operation(summary = "Asignar una plantilla a una persona",
            description = "Desde hoy o una fecha futura, nunca pasada. Si la plantilla se separa de la "
                    + "jornada contratada más de 30 minutos a la semana, se asigna igual, la respuesta "
                    + "lo dice en 'aviso' y se avisa a quien lleva los contratos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asignada",
                    content = @Content(schema = @Schema(implementation = ScheduleAssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Fecha pasada o fechas al revés",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empleado o plantilla no encontrados",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya tiene un cuadrante en alguna de esas fechas",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/asignaciones")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<ScheduleAssignmentResponse> asignar(
            @Valid @RequestBody ScheduleAssignmentRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(scheduleService.asignar(request, usuario.getUser()));
    }

    @Operation(summary = "Cerrar una asignación de cuadrante",
            description = "Pone el último día en que aplica. No puede ser anterior a ayer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cerrada",
                    content = @Content(schema = @Schema(implementation = ScheduleAssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Fecha anterior a ayer o al inicio",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Asignación no encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya terminó antes de ayer",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/asignaciones/{asignacionId}")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<ScheduleAssignmentResponse> cerrarAsignacion(
            @PathVariable long asignacionId,
            @Valid @RequestBody CloseScheduleAssignmentRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(scheduleService.cerrarAsignacion(asignacionId, request.fechaFin(), usuario.getUser()));
    }

    @Operation(summary = "Borrar una asignación que aún no ha empezado",
            description = "Para deshacer un error. Una que ya ha estado en vigor no se borra: se cierra.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Borrada"),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Asignación no encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya ha estado en vigor",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/asignaciones/{asignacionId}")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<Void> borrarAsignacion(
            @PathVariable long asignacionId, @AuthenticationPrincipal SecurityUser usuario) {
        scheduleService.borrarAsignacion(asignacionId, usuario.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Las asignaciones de cuadrante de una persona", description = "De la más reciente a la más antigua.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asignaciones",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleAssignmentResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/usuarios/{usuarioId}/asignaciones")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<List<ScheduleAssignmentResponse>> asignacionesDe(
            @PathVariable long usuarioId, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(scheduleService.asignacionesDe(usuarioId, usuario.getUser()));
    }

    // ------------------------------------------------------------------
    // Excepciones
    // ------------------------------------------------------------------

    @Operation(summary = "Poner una excepción en un día",
            description = "LIBRE (sin tramos) o TRAMO (uno o varios, que sustituyen a los de la plantilla "
                    + "ese día). De hoy en adelante, y solo a quien tenga cuadrante ese día. Los festivos "
                    + "y las ausencias no van aquí: ya salen del calendario y de las ausencias aprobadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Creada: una fila por tramo, o una para LIBRE",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleExceptionResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Día pasado, sin cuadrante, o tramos inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Choca con otra excepción de ese día",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/excepciones")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<List<ScheduleExceptionResponse>> crearExcepcion(
            @Valid @RequestBody ScheduleExceptionRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(scheduleService.crearExcepcion(request, usuario.getUser()));
    }

    @Operation(summary = "Quitar una excepción", description = "Solo de hoy en adelante.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Quitada"),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Excepción no encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Es de un día pasado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/excepciones/{excepcionId}")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<Void> borrarExcepcion(
            @PathVariable long excepcionId, @AuthenticationPrincipal SecurityUser usuario) {
        scheduleService.borrarExcepcion(excepcionId, usuario.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Las excepciones de una persona", description = "De la más reciente a la más antigua.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Excepciones",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleExceptionResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Sin 'cuadrante:gestionar', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/usuarios/{usuarioId}/excepciones")
    @PreAuthorize("hasAuthority('cuadrante:gestionar')")
    public ResponseEntity<List<ScheduleExceptionResponse>> excepcionesDe(
            @PathVariable long usuarioId, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(scheduleService.excepcionesDe(usuarioId, usuario.getUser()));
    }
}
