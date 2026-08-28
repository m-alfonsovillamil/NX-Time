package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.dto.RefreshTokenRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import com.nxtime.nxtime.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador que gestiona el acceso público (Login y Registro).
 *
 * /auth/refresh y /auth/logout, nuevos en la Fase 4: el access token
 * dura poco (15 min) a propósito; el refresh token es lo que permite
 * renovarlo sin volver a pedir contraseña, y revocarlo cierra la
 * sesión de verdad (antes un token robado era válido 24h sin ninguna
 * forma de invalidarlo -- ver auditoría, defectos de diseño).
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Registro de empresa, login, renovación y cierre de sesión. "
        + "Público (sin token), pero limitado a 10 peticiones/minuto por IP en login y register-manager.")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Registrar una empresa nueva",
            description = "Crea la empresa y a quien la registra como ADMIN de ese tenant. "
                    + "Es quien luego puede crear GESTOR/RRHH/otros ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empresa creada, tokens emitidos",
                    content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (email, nombre o contraseña)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe una empresa con ese nombre",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos desde esta IP",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/register-manager")
    public ResponseEntity<AuthenticationResponse> registerManager(@Valid @RequestBody RegisterManagerRequest request) {
        return ResponseEntity.ok(authService.registerManager(request));
    }

    @Operation(summary = "Iniciar sesión", description = "Devuelve un access token (15 min) y un refresh token (30 días).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login correcto",
                    content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Email o contraseña en blanco / email mal formado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas, o usuario dado de baja",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos desde esta IP",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Renovar el access token",
            description = "Emite un access token nuevo a partir de un refresh token vivo. Reutiliza el mismo refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access token renovado",
                    content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
            @ApiResponse(responseCode = "400", description = "refreshToken en blanco",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token inexistente, revocado o caducado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshAccessToken(request.refreshToken()));
    }

    @Operation(summary = "Cerrar sesión", description = "Revoca el refresh token indicado. Idempotente: "
            + "si el token no existe, no lanza error ni revela nada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión cerrada (o ya lo estaba)"),
            @ApiResponse(responseCode = "400", description = "refreshToken en blanco",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok().build();
    }
}
