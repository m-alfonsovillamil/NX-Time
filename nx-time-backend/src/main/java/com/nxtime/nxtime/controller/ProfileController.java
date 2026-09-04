package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.ProfileResponse;
import com.nxtime.nxtime.dto.UpdateProfileRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.EmployeeProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El perfil propio y el de los compañeros (Fase B).
 *
 * Separado de {@code /api/v1/gestor}: aquí viven las operaciones sobre
 * uno mismo, que no piden authority ninguna más allá de tener sesión.
 * Lo que un empleado NO puede cambiarse -- rol, jornada, vacaciones,
 * departamento -- no está en {@link UpdateProfileRequest} y se queda en
 * los endpoints de gestión, detrás de sus authorities.
 */
@RestController
@RequestMapping("/api/v1/perfil")
@Tag(name = "Perfil", description = "Datos personales del usuario autenticado, y consulta del perfil "
        + "de un compañero de la misma empresa.")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final EmployeeProfileService employeeProfileService;

    public ProfileController(EmployeeProfileService employeeProfileService) {
        this.employeeProfileService = employeeProfileService;
    }

    @Operation(summary = "Mi perfil",
            description = "Datos personales y laborales, con el nombre completo y las iniciales del "
                    + "avatar ya calculados para que no los arme cada cliente a su manera.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileResponse> getMyProfile(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(employeeProfileService.getMyProfile(usuario.getUser()));
    }

    @Operation(summary = "Cambiar mis datos personales",
            description = "Nombre, apellidos, fecha de nacimiento y puesto. Es un PATCH: lo que va a "
                    + "null no se toca, y una cadena vacía borra el dato. NO incluye rol, jornada, "
                    + "vacaciones ni departamento -- eso no lo decide uno sobre sí mismo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Fecha de nacimiento futura, nombre vacío "
                    + "o algún campo demasiado largo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(employeeProfileService.updateMyProfile(request, usuario.getUser()));
    }

    @Operation(summary = "El perfil de un compañero",
            description = "Solo de la misma empresa (aislamiento multi-tenant a mano, ADR 006).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empleado:leer', "
                    + "o usuario de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{usuarioId}")
    @PreAuthorize("hasAuthority('empleado:leer')")
    public ResponseEntity<ProfileResponse> getProfile(
            @PathVariable long usuarioId, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(employeeProfileService.getProfile(usuarioId, usuario.getUser()));
    }
}
