package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.EquipoAhoraResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.EquipoAhoraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quién está trabajando ahora y quién no ha fichado hoy (plan N, paso N7).
 *
 * Aparte de {@code ManagerController} para no tocar su constructor ni sus
 * tests por un solo endpoint de lectura. Va bajo {@code /gestor} como el resto
 * de lo del equipo, y lo abre la misma authority que el historial del equipo.
 */
@RestController
@RequestMapping("/api/v1/gestor")
@Tag(name = "Gestión")
@SecurityRequirement(name = "bearerAuth")
public class EquipoAhoraController {

    private final EquipoAhoraService equipoAhoraService;

    public EquipoAhoraController(EquipoAhoraService equipoAhoraService) {
        this.equipoAhoraService = equipoAhoraService;
    }

    @Operation(summary = "Quién está trabajando ahora y quién no ha fichado hoy",
            description = "Una foto de este momento de la plantilla activa: cuántos trabajan, están en pausa, "
                    + "han terminado, no han fichado o hoy no les toca, y persona a persona desde cuándo. "
                    + "«conRetraso» marca a quien no ha fichado pasados diez minutos de la entrada de su "
                    + "cuadrante. Primero sale lo que pide atención. Sin la ubicación.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El equipo ahora",
                    content = @Content(schema = @Schema(implementation = EquipoAhoraResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer:equipo'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer:equipo')")
    @GetMapping("/equipo-ahora")
    public ResponseEntity<EquipoAhoraResponse> ahora(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(equipoAhoraService.ahora(usuario.getUser()));
    }
}
