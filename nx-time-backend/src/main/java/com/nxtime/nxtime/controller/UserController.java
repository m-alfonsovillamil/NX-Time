package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.ChangePasswordRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.AuthService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador para la gestión del perfil de usuario.
 */
@RestController
@RequestMapping("/api/v1/usuario")
@Tag(name = "Usuario", description = "Operaciones sobre el propio perfil del usuario autenticado.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Cambiar la contraseña propia", description = "Exige la contraseña antigua.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contraseña cambiada"),
            @ApiResponse(responseCode = "400", description = "Contraseña nueva demasiado corta, "
                    + "o la contraseña antigua no coincide",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/cambiar-contrasena")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal SecurityUser user) {
        authService.changePassword(request, user.getUser());
        return ResponseEntity.ok().build();
    }
}
