package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.ProjectAssignmentRequest;
import com.nxtime.nxtime.dto.ProjectAssignmentResponse;
import com.nxtime.nxtime.dto.ProjectDetailResponse;
import com.nxtime.nxtime.dto.ProjectHoursResponse;
import com.nxtime.nxtime.dto.ProjectRequest;
import com.nxtime.nxtime.dto.ProjectResponse;
import com.nxtime.nxtime.dto.UpdateProjectStatusRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.ProjectService;
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
import java.time.YearMonth;
import java.time.ZoneId;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proyectos y horas por proyecto (Fase D).
 *
 * Leer el listado y los propios proyectos es {@code proyecto:leer} (todo
 * el mundo: saber en qué proyecto estás es parte de tu ficha). Crear,
 * editar, asignar y cerrar es {@code proyecto:gestionar}, desde GESTOR.
 *
 * <b>Sacar a alguien de un proyecto no borra su asignación</b>, le pone
 * fecha de fin: borrarla haría desaparecer sus horas pasadas de ese
 * proyecto, que es justo lo que el diseño de la fase quiere evitar.
 */
@RestController
@RequestMapping("/api/v1/proyectos")
@Tag(name = "Proyectos", description = "Proyectos de la empresa, asignaciones con vigencia y horas imputadas.")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    /** "El mes actual" es el de España, no el de UTC (ver TimeEntryMapper). */
    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "Proyectos de mi empresa",
            description = "Ordenados por código, con cuántas asignaciones tiene cada uno: es lo que "
                    + "decide si se puede ofrecer el botón de borrar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProjectResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'proyecto:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('proyecto:leer')")
    public ResponseEntity<List<ProjectResponse>> listar(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(projectService.listar(usuario.getUser()));
    }

    @Operation(summary = "Un proyecto en detalle",
            description = "Sus datos, quién ha pasado por él y cuántas horas puso cada uno en el mes "
                    + "indicado. Sin parámetros, el mes en curso. Ojo: en 'asignaciones' está el "
                    + "histórico completo y en 'horas' solo quien trabajó ese mes; no son la misma "
                    + "gente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle",
                    content = @Content(schema = @Schema(implementation = ProjectDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Mes fuera de 1..12",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin authority, o proyecto de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Proyecto no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('proyecto:leer')")
    public ResponseEntity<ProjectDetailResponse> detalle(
            @PathVariable long id,
            @Parameter(description = "Año. Por defecto, el actual.")
            @RequestParam(required = false) Integer anio,
            @Parameter(description = "Mes (1-12). Por defecto, el actual.")
            @RequestParam(required = false) Integer mes,
            @AuthenticationPrincipal SecurityUser usuario) {
        YearMonth ahora = YearMonth.now(MADRID_ZONE);
        return ResponseEntity.ok(projectService.detalle(
                id,
                anio != null ? anio : ahora.getYear(),
                mes != null ? mes : ahora.getMonthValue(),
                usuario.getUser()));
    }

    @Operation(summary = "Horas por proyecto del mes",
            description = "Total de la empresa, repartido por proyecto y de mayor a menor. Solo "
                    + "aparecen los proyectos con horas: un listado lleno de ceros no dice nada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Horas del mes",
                    content = @Content(schema = @Schema(implementation = ProjectHoursResponse.class))),
            @ApiResponse(responseCode = "400", description = "Mes fuera de 1..12",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer:equipo'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/horas")
    // Es un agregado de TODA la empresa, como el panel de indicadores, así
    // que va con la misma authority que ese panel y no con 'proyecto:leer':
    // saber en qué proyecto estás tú no es lo mismo que ver cuántas horas
    // ha puesto cada equipo.
    @PreAuthorize("hasAuthority('fichaje:leer:equipo')")
    public ResponseEntity<ProjectHoursResponse> horas(
            @Parameter(description = "Año. Por defecto, el actual.")
            @RequestParam(required = false) Integer anio,
            @Parameter(description = "Mes (1-12). Por defecto, el actual.")
            @RequestParam(required = false) Integer mes,
            @AuthenticationPrincipal SecurityUser usuario) {
        YearMonth ahora = YearMonth.now(MADRID_ZONE);
        return ResponseEntity.ok(projectService.horasDelMes(
                anio != null ? anio : ahora.getYear(),
                mes != null ? mes : ahora.getMonthValue(),
                usuario.getUser()));
    }

    @Operation(summary = "Los proyectos por los que ha pasado un empleado",
            description = "El histórico de asignaciones con su vigencia. Es lo que se enseña en el "
                    + "perfil.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asignaciones",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProjectAssignmentResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Empleado de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/empleados/{usuarioId}")
    @PreAuthorize("hasAuthority('proyecto:leer')")
    public ResponseEntity<List<ProjectAssignmentResponse>> deEmpleado(
            @PathVariable long usuarioId, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(projectService.asignacionesDe(usuarioId, usuario.getUser()));
    }

    @Operation(summary = "Crear un proyecto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Creado",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o fecha de fin anterior a la de inicio",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'proyecto:gestionar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe un proyecto con ese código",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('proyecto:gestionar')")
    public ResponseEntity<ProjectResponse> crear(
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(projectService.crear(request, usuario.getUser()));
    }

    @Operation(summary = "Editar un proyecto",
            description = "No cambia si está activo: para cerrarlo o reabrirlo está /estado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Actualizado",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin authority, o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Proyecto no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe un proyecto con ese código",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('proyecto:gestionar')")
    public ResponseEntity<ProjectResponse> editar(
            @PathVariable long id,
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(projectService.editar(id, request, usuario.getUser()));
    }

    @Operation(summary = "Cerrar o reabrir un proyecto",
            description = "Cerrar NO borra nada: las horas ya imputadas siguen contando.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado cambiado",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin authority, o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Proyecto no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('proyecto:gestionar')")
    public ResponseEntity<ProjectResponse> cambiarEstado(
            @PathVariable long id,
            @Valid @RequestBody UpdateProjectStatusRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(
                projectService.cambiarEstado(id, request.activo(), usuario.getUser()));
    }

    @Operation(summary = "Borrar un proyecto",
            description = "Falla con 409 si tiene asignaciones. Un proyecto terminado se CIERRA, no "
                    + "se borra: borrarlo haría desaparecer el reparto de horas ya informado.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Borrado"),
            @ApiResponse(responseCode = "403", description = "Sin authority, o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Proyecto no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Todavía tiene asignaciones",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('proyecto:gestionar')")
    public ResponseEntity<Void> borrar(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        projectService.borrar(id, usuario.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Asignar a alguien al proyecto",
            description = "Con fechaFin null queda asignado hasta nuevo aviso. Falla con 409 si esa "
                    + "persona ya está en otro proyecto en alguno de esos días: nadie puede estar en "
                    + "dos a la vez, y lo impone la base de datos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asignado",
                    content = @Content(schema = @Schema(implementation = ProjectAssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o fechas incoherentes",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin authority, o proyecto/empleado de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Proyecto o empleado no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya está asignado a otro proyecto en esas fechas",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/asignaciones")
    @PreAuthorize("hasAuthority('proyecto:gestionar')")
    public ResponseEntity<ProjectAssignmentResponse> asignar(
            @PathVariable long id,
            @Valid @RequestBody ProjectAssignmentRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(projectService.asignar(id, request, usuario.getUser()));
    }

    @Operation(summary = "Sacar a alguien del proyecto",
            description = "Pone la fecha de fin. NO borra la asignación: sus horas pasadas siguen "
                    + "imputadas a este proyecto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asignación cerrada",
                    content = @Content(schema = @Schema(implementation = ProjectAssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Falta la fecha, o es anterior al inicio",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Asignación de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Asignación no encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "La fecha choca con otra asignación suya",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/asignaciones/{asignacionId}")
    @PreAuthorize("hasAuthority('proyecto:gestionar')")
    public ResponseEntity<ProjectAssignmentResponse> finalizarAsignacion(
            @PathVariable long asignacionId,
            @Valid @RequestBody FinalizarAsignacionRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(projectService.finalizarAsignacion(
                asignacionId, request.fechaFin(), usuario.getUser()));
    }

    /**
     * Cuerpo de "sacar del proyecto". Es un record anidado y no un DTO
     * suelto porque no lo usa nadie más: un solo campo que solo tiene
     * sentido en este endpoint.
     */
    public record FinalizarAsignacionRequest(java.time.LocalDate fechaFin) {
    }
}
