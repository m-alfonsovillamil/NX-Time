package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.RegisterPushDeviceRequest;
import com.nxtime.nxtime.dto.UnregisterPushDeviceRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.PushDeviceService;
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
 * Los dispositivos que reciben push (Fase B5, ADR 028).
 *
 * Sin authority propia: recibir en tu móvil los avisos que ya puedes leer en
 * la app no es un privilegio, es tuyo. Basta con estar autenticado.
 */
@RestController
@RequestMapping("/api/v1/dispositivos-push")
@Tag(name = "Push", description = "Registrar y dar de baja el dispositivo que recibe las notificaciones push.")
@SecurityRequirement(name = "bearerAuth")
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    public PushDeviceController(PushDeviceService pushDeviceService) {
        this.pushDeviceService = pushDeviceService;
    }

    @Operation(summary = "Registrar este dispositivo",
            description = "Idempotente: la app lo llama al entrar. Si el token era de otra persona (un móvil "
                    + "compartido), pasa a ser de quien lo registra.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Registrado"),
            @ApiResponse(responseCode = "400", description = "Sin token o plataforma desconocida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> registrar(
            @Valid @RequestBody RegisterPushDeviceRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        pushDeviceService.registrar(usuario.getUser(), request.token(), request.plataforma());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Dar de baja este dispositivo",
            description = "Al cerrar sesión o al apagar los push en Ajustes. Solo borra un dispositivo propio; "
                    + "si el token no existe o es de otra persona, no hace nada y responde igual.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Dado de baja (o no había nada que dar de baja)"),
            @ApiResponse(responseCode = "400", description = "Sin token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/baja")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> darDeBaja(
            @Valid @RequestBody UnregisterPushDeviceRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        pushDeviceService.darDeBaja(usuario.getUser(), request.token());
        return ResponseEntity.noContent().build();
    }
}
