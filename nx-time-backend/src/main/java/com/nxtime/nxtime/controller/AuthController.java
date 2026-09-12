package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.dto.PasswordRecoveryRequest;
import com.nxtime.nxtime.dto.PasswordResetRequest;
import com.nxtime.nxtime.dto.RefreshTokenRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import com.nxtime.nxtime.service.AccessCodeService;
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
 *
 * /auth/recuperar y /auth/recuperar/confirmar (09/2026, ADR 014): elegir
 * contraseña con un código que llega por correo, sirva para recuperarla o
 * para entrar por primera vez.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Registro de empresa, login, renovación y cierre de sesión, y elegir "
        + "contraseña con un código. Público (sin token), pero limitado a 10 peticiones/minuto por IP en login, "
        + "register-manager y recuperar.")
public class AuthController {

    private final AuthService authService;
    private final AccessCodeService accessCodeService;

    public AuthController(AuthService authService, AccessCodeService accessCodeService) {
        this.authService = authService;
        this.accessCodeService = accessCodeService;
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

    @Operation(summary = "Pedir un código para elegir contraseña",
            description = "Para quien la ha olvidado, o no llegó a usar el código de alta. Si el correo tiene una "
                    + "cuenta activa, le llega un código de 6 dígitos que caduca en 15 minutos. Responde 202 "
                    + "SIEMPRE, tenga cuenta o no: si no, cualquiera podría averiguar quién la tiene probando "
                    + "direcciones.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Petición recibida, haya o no una cuenta con ese correo"),
            @ApiResponse(responseCode = "400", description = "Email en blanco o mal formado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos desde esta IP",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/recuperar")
    public ResponseEntity<Void> solicitarRecuperacion(@Valid @RequestBody PasswordRecoveryRequest request) {
        accessCodeService.solicitarRecuperacion(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Elegir contraseña con un código",
            description = "Vale igual el código de alta que el de recuperación. Cierra todas las sesiones abiertas "
                    + "de la cuenta. Cada código admite 5 intentos: al quinto fallo se anula.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contraseña fijada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o un código incorrecto, caducado, "
                    + "usado o anulado. El mensaje es el mismo en todos los casos, y también si el correo no tiene cuenta.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos desde esta IP",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/recuperar/confirmar")
    public ResponseEntity<Void> confirmarRecuperacion(@Valid @RequestBody PasswordResetRequest request) {
        accessCodeService.confirmar(request.email(), request.codigo(), request.contrasenaNueva());
        return ResponseEntity.noContent().build();
    }
}
