package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.dto.PasswordRecoveryRequest;
import com.nxtime.nxtime.dto.PasswordResetRequest;
import com.nxtime.nxtime.dto.RefreshTokenRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.security.SesionWeb;
import com.nxtime.nxtime.service.AccessCodeService;
import com.nxtime.nxtime.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
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
 *
 * Desde la fase W1 (ADR 030) el navegador no ve su refresh token: el login con
 * {@code origen: WEB} lo pone en una cookie {@code HttpOnly}, y refresh y
 * logout lo leen de ahí cuando el cuerpo no lo trae, exigiendo entonces el
 * CSRF de {@link SesionWeb}. Con el refresh en el cuerpo todo sigue como
 * antes: es lo que hace la app Android.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Registro de empresa, login, renovación y cierre de sesión, y elegir "
        + "contraseña con un código. Público (sin token), pero limitado a 10 peticiones/minuto por IP en login, "
        + "register-manager y recuperar.")
public class AuthController {

    private static final String REFRESH_OBLIGATORIO = "El refresh token es obligatorio.";

    private final AuthService authService;
    private final AccessCodeService accessCodeService;
    private final SesionWeb sesionWeb;

    public AuthController(AuthService authService, AccessCodeService accessCodeService, SesionWeb sesionWeb) {
        this.authService = authService;
        this.accessCodeService = accessCodeService;
        this.sesionWeb = sesionWeb;
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
    public ResponseEntity<AuthenticationResponse> registerManager(
            @Valid @RequestBody RegisterManagerRequest request, HttpServletResponse respuesta) {
        AuthenticationResponse sesion = authService.registerManager(request);
        // Como el login: desde el navegador, el refresh a la cookie (ADR 030).
        if (!esNavegador(request.origen())) {
            return ResponseEntity.ok(sesion);
        }
        sesionWeb.emitir(respuesta, sesion.refreshToken());
        return ResponseEntity.ok(sesion.sinRefreshToken());
    }

    @Operation(summary = "Iniciar sesión",
            description = "Devuelve un access token (15 min) y un refresh token, cuya duración depende de "
                    + "'origen': 30 días desde ANDROID o IOS, 12 horas desde WEB. Devuelve también "
                    + "'authorities', lo que esta persona puede hacer, para que el cliente arme su menú "
                    + "sin copiarse el reparto de permisos del servidor. Viaja aquí y no solo en "
                    + "GET /api/v1/perfil porque si no habría un hueco, el primero tras entrar, en el que "
                    + "la aplicación no sabría qué ofrecer. Con 'origen' WEB el refresh NO viaja en el cuerpo: va "
                    + "en la cookie HttpOnly nx_refresh, junto con la cookie nx_csrf (ADR 030).")
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
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse respuesta) {
        AuthenticationResponse sesion = authService.login(request);
        if (!esNavegador(request.origen())) {
            return ResponseEntity.ok(sesion);
        }
        sesionWeb.emitir(respuesta, sesion.refreshToken());
        return ResponseEntity.ok(sesion.sinRefreshToken());
    }

    private static boolean esNavegador(String origen) {
        return origen != null && "WEB".equals(origen.trim().toUpperCase(Locale.ROOT));
    }

    /** El refresh del cuerpo, si lo trae: es la app. */
    private static Optional<String> refreshDelCuerpo(RefreshTokenRequest request) {
        return Optional.ofNullable(request)
                .map(RefreshTokenRequest::refreshToken)
                .filter(token -> !token.isBlank());
    }

    @Operation(summary = "Renovar el access token",
            description = "Emite un access token nuevo a partir de un refresh token vivo, y **rota el refresh**: "
                    + "el que se envía deja de valer y la respuesta trae uno distinto, que el cliente debe "
                    + "guardar. Reenviar uno ya rotado se interpreta como una copia robada y revoca la "
                    + "sesión entera. Devuelve también 'authorities', así que un cambio de rol llega sin "
                    + "necesidad de volver a entrar. Sin cuerpo, lo lee de la cookie nx_refresh (el navegador), y "
                    + "entonces exige la cabecera X-CSRF-Token igual a la cookie nx_csrf y un Origin permitido; la "
                    + "respuesta trae las cookies nuevas y ningún refresh en el cuerpo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access token renovado",
                    content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Sin refreshToken en el cuerpo ni cookie nx_refresh",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token inexistente, revocado o caducado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Con la cookie, sin el CSRF correcto o desde un origen "
                    + "no permitido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        Optional<String> delCuerpo = refreshDelCuerpo(request);
        if (delCuerpo.isPresent()) {
            return ResponseEntity.ok(authService.refreshAccessToken(delCuerpo.get()));
        }
        String deLaCookie = sesionWeb.refreshDeLaCookie(peticion)
                .orElseThrow(() -> new BusinessException(REFRESH_OBLIGATORIO, HttpStatus.BAD_REQUEST));
        // El CSRF antes de tocar el token: una petición de otra web no llega
        // ni a rotarlo, que ya sería un efecto.
        sesionWeb.comprobarCsrf(peticion);
        AuthenticationResponse sesion = authService.refreshAccessToken(deLaCookie);
        sesionWeb.emitir(respuesta, sesion.refreshToken());
        return ResponseEntity.ok(sesion.sinRefreshToken());
    }

    @Operation(summary = "Cerrar sesión", description = "Revoca el refresh token indicado. Idempotente: "
            + "si el token no existe, no lanza error ni revela nada. Sin cuerpo, lo lee de la cookie nx_refresh "
            + "(con el mismo CSRF que /auth/refresh) y borra las cookies de la sesión web.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión cerrada (o ya lo estaba)"),
            @ApiResponse(responseCode = "400", description = "Sin refreshToken en el cuerpo ni cookie nx_refresh",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Con la cookie, sin el CSRF correcto o desde un origen "
                    + "no permitido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest peticion,
            HttpServletResponse respuesta) {
        Optional<String> delCuerpo = refreshDelCuerpo(request);
        if (delCuerpo.isPresent()) {
            authService.logout(delCuerpo.get());
            return ResponseEntity.ok().build();
        }
        String deLaCookie = sesionWeb.refreshDeLaCookie(peticion)
                .orElseThrow(() -> new BusinessException(REFRESH_OBLIGATORIO, HttpStatus.BAD_REQUEST));
        // También aquí: sin CSRF, otra web podría cerrar la sesión de alguien.
        sesionWeb.comprobarCsrf(peticion);
        authService.logout(deLaCookie);
        sesionWeb.borrar(respuesta);
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
