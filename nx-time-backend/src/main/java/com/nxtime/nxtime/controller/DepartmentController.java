package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.DepartmentRequest;
import com.nxtime.nxtime.dto.DepartmentResponse;
import com.nxtime.nxtime.dto.ProfileResponse;
import com.nxtime.nxtime.dto.UpdateDepartmentAssignmentRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.DepartmentService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Departamentos de la empresa (Fase B).
 *
 * <b>Leerlos y gestionarlos son dos authorities distintas.</b> El
 * listado lo necesita cualquiera que mire un perfil, así que va con
 * {@code empleado:leer}; crear, renombrar y borrar es organizar la
 * empresa y va con {@code departamento:gestionar} (RRHH+).
 */
@RestController
@RequestMapping("/api/v1/departamentos")
@Tag(name = "Departamentos", description = "Departamentos de la empresa y a cuál pertenece cada empleado.")
@SecurityRequirement(name = "bearerAuth")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final EmployeeProfileService employeeProfileService;

    public DepartmentController(DepartmentService departmentService,
                                EmployeeProfileService employeeProfileService) {
        this.departmentService = departmentService;
        this.employeeProfileService = employeeProfileService;
    }

    @Operation(summary = "Departamentos de mi empresa",
            description = "Ordenados por nombre, con cuánta gente tiene cada uno: es lo que decide si "
                    + "se puede ofrecer el botón de borrar, porque uno con plantilla no se borra.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DepartmentResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empleado:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('empleado:leer')")
    public ResponseEntity<List<DepartmentResponse>> listar(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(departmentService.listar(usuario.getUser()));
    }

    @Operation(summary = "Crear un departamento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Creado",
                    content = @Content(schema = @Schema(implementation = DepartmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Nombre vacío o demasiado largo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'departamento:gestionar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe uno con ese nombre en la empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('departamento:gestionar')")
    public ResponseEntity<DepartmentResponse> crear(
            @Valid @RequestBody DepartmentRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(departmentService.crear(request, usuario.getUser()));
    }

    @Operation(summary = "Renombrar un departamento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Renombrado",
                    content = @Content(schema = @Schema(implementation = DepartmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Nombre vacío o demasiado largo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin authority, o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Departamento no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe uno con ese nombre",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('departamento:gestionar')")
    public ResponseEntity<DepartmentResponse> renombrar(
            @PathVariable long id,
            @Valid @RequestBody DepartmentRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(departmentService.renombrar(id, request, usuario.getUser()));
    }

    @Operation(summary = "Borrar un departamento",
            description = "Falla con 409 si todavía tiene gente dentro: primero hay que moverla.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Borrado"),
            @ApiResponse(responseCode = "403", description = "Sin authority, o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Departamento no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Todavía tiene empleados",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('departamento:gestionar')")
    public ResponseEntity<Void> borrar(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        departmentService.borrar(id, usuario.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Poner a un empleado en un departamento",
            description = "Con departamentoId a null se le saca del que tuviera. Va aquí y no en el "
                    + "perfil propio a propósito: a qué departamento perteneces no lo decides tú.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asignado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin authority, o empleado/departamento de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empleado o departamento no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/empleados/{usuarioId}")
    @PreAuthorize("hasAuthority('departamento:gestionar')")
    public ResponseEntity<ProfileResponse> asignar(
            @PathVariable long usuarioId,
            @RequestBody UpdateDepartmentAssignmentRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(
                employeeProfileService.assignDepartment(usuarioId, request.departamentoId(), usuario.getUser()));
    }
}
