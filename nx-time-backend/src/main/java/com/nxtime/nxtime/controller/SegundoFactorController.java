package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.ActivacionSegundoFactorResponse;
import com.nxtime.nxtime.dto.CambioSegundoFactorRequest;
import com.nxtime.nxtime.dto.CodigoSegundoFactorRequest;
import com.nxtime.nxtime.dto.CodigosDeRecuperacionResponse;
import com.nxtime.nxtime.dto.SegundoFactorResponse;
import com.nxtime.nxtime.security.JwtService;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.SegundoFactorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * El segundo factor (ADR 051): el de cada uno, en su perfil, y quitárselo a
 * alguien de la empresa que ha perdido el móvil.
 */
@RestController
@Tag(name = "Segundo factor", description = "Un código de una aplicación de autenticación al entrar con contraseña "
        + "(TOTP, RFC 6238), con códigos de recuperación de un solo uso. Opcional, salvo para el operador de la "
        + "plataforma, el Responsable del canal de denuncias y, si su empresa lo exige, los ADMIN y RRHH.")
@SecurityRequirement(name = "bearerAuth")
public class SegundoFactorController {

    private final SegundoFactorService segundoFactor;
    private final JwtService jwtService;

    public SegundoFactorController(SegundoFactorService segundoFactor, JwtService jwtService) {
        this.segundoFactor = segundoFactor;
        this.jwtService = jwtService;
    }

    @Operation(summary = "Mi segundo factor", description = "Si está activo, si se me exige y por qué, y cuántos "
            + "códigos de recuperación me quedan.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado",
                    content = @Content(schema = @Schema(implementation = SegundoFactorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/api/v1/perfil/segundo-factor")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SegundoFactorResponse> estado(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(segundoFactor.estado(usuario.getUser()));
    }

    @Operation(summary = "Empezar a activarlo", description = "Un secreto nuevo, con la dirección otpauth:// para el "
            + "QR. No se pide al entrar hasta confirmarlo con un código. Repetirlo antes de confirmar cambia el secreto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Secreto nuevo",
                    content = @Content(schema = @Schema(implementation = ActivacionSegundoFactorResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya está activo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/api/v1/perfil/segundo-factor/activacion")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ActivacionSegundoFactorResponse> iniciar(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(segundoFactor.iniciarActivacion(usuario.getUser()));
    }

    @Operation(summary = "Confirmar la activación", description = "Con la contraseña y un código del secreto nuevo. "
            + "Devuelve los diez códigos de recuperación: es la única vez que se ven. La sesión desde la que se "
            + "confirma pasa a contar como sesión con segundo factor: el cliente tiene que renovar el access token "
            + "(/auth/refresh) para que lo diga. Avisa por correo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Activado",
                    content = @Content(schema = @Schema(implementation = CodigosDeRecuperacionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Contraseña o código incorrectos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "No hay activación empezada, o ya está activo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos con esta cuenta",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/api/v1/perfil/segundo-factor/activacion/confirmar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CodigosDeRecuperacionResponse> confirmar(
            @Valid @RequestBody CambioSegundoFactorRequest request,
            @AuthenticationPrincipal SecurityUser usuario,
            @Parameter(hidden = true)
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacion) {
        // La sesión desde la que se activa, como al cambiar la contraseña (UserController).
        Optional<UUID> sesion = autorizacion != null && autorizacion.startsWith("Bearer ")
                ? jwtService.extractSesion(autorizacion.substring(7))
                : Optional.empty();
        return ResponseEntity.ok(new CodigosDeRecuperacionResponse(
                segundoFactor.confirmarActivacion(usuario.getUser(), request.contrasena(), request.codigo(), sesion)));
    }

    @Operation(summary = "Desactivarlo", description = "Con la contraseña y un código (de la aplicación o de "
            + "recuperación). No se puede si se me exige. Avisa por correo.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Desactivado"),
            @ApiResponse(responseCode = "400", description = "Contraseña o código incorrectos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Se me exige: no lo puedo quitar",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "No está activo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos con esta cuenta",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/api/v1/perfil/segundo-factor/desactivar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> desactivar(
            @Valid @RequestBody CambioSegundoFactorRequest request, @AuthenticationPrincipal SecurityUser usuario) {
        segundoFactor.desactivar(usuario.getUser(), request.contrasena(), request.codigo());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Códigos de recuperación nuevos", description = "Con un código de la aplicación. Los de "
            + "antes dejan de valer. Avisa por correo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Códigos nuevos",
                    content = @Content(schema = @Schema(implementation = CodigosDeRecuperacionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Código incorrecto",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "No está activo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos con esta cuenta",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/api/v1/perfil/segundo-factor/codigos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CodigosDeRecuperacionResponse> regenerarCodigos(
            @Valid @RequestBody CodigoSegundoFactorRequest request, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(new CodigosDeRecuperacionResponse(
                segundoFactor.regenerarCodigos(usuario.getUser(), request.codigo())));
    }

    @Operation(summary = "Quitar el segundo factor a alguien de la empresa",
            description = "Para quien ha perdido el móvil y los códigos de recuperación: vuelve a entrar solo con "
                    + "la contraseña y lo activa otra vez. No a uno mismo ni a un rol por encima del propio. Le avisa "
                    + "por correo. Si no lo tenía, no hace nada.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Quitado (o no lo tenía)"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'empleado:gestionar', de otra empresa, uno mismo o "
                    + "un rol por encima",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/api/v1/gestor/empleados/{id}/segundo-factor")
    @PreAuthorize("hasAuthority('empleado:gestionar')")
    public ResponseEntity<Void> quitar(@PathVariable long id, @AuthenticationPrincipal SecurityUser quien) {
        segundoFactor.quitar(id, quien.getUser());
        return ResponseEntity.noContent().build();
    }
}
