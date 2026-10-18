package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.ComplaintChannelSettingsResponse;
import com.nxtime.nxtime.dto.UpdateComplaintChannelRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.impl.CanalDeDenuncias;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El Responsable del Sistema Interno de Información y la vía verbal del canal
 * de denuncias (Ley 2/2023, art. 8; ADR 048). Solo el ADMIN: el nombramiento
 * lo hace el órgano de administración de la empresa.
 */
@RestController
@RequestMapping("/api/v1/empresa/canal-denuncias")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Empresa", description = "La configuración de la empresa")
public class CompanyComplaintChannelController {

    private final CanalDeDenuncias canal;

    public CompanyComplaintChannelController(CanalDeDenuncias canal) {
        this.canal = canal;
    }

    @Operation(summary = "El canal de denuncias de mi empresa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cómo está montado",
                    content = @Content(schema = @Schema(implementation = ComplaintChannelSettingsResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'empresa:configurar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<ComplaintChannelSettingsResponse> leer(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(canal.ajustes(usuario.getUser()));
    }

    @Operation(summary = "Designar al Responsable del Sistema y la vía verbal",
            description = "Desde que hay Responsable, solo él instruye: los ADMIN dejan de ver el canal. Sin "
                    + "Responsable (null), instruyen los ADMIN. La empresa tiene 10 días hábiles para comunicar el "
                    + "nombramiento o el cese a la Autoridad Independiente de Protección del Informante.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Guardado",
                    content = @Content(schema = @Schema(implementation = ComplaintChannelSettingsResponse.class))),
            @ApiResponse(responseCode = "400", description = "La persona no es de la empresa o no está activa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'empresa:configurar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<ComplaintChannelSettingsResponse> guardar(
            @Valid @RequestBody UpdateComplaintChannelRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(canal.guardar(request, usuario.getUser()));
    }
}
