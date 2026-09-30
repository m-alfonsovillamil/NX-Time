package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.CompanySettingsResponse;
import com.nxtime.nxtime.dto.UpdateCompanySettingsRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.CompanySettingsService;
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
 * Ajustes de la empresa (fase Z2): su nombre y su zona horaria (ADR 032).
 *
 * Solo ADMIN ({@code empresa:configurar}): la zona decide a qué día pertenece
 * cada fichaje de todo el histórico, y cambiarla puede invalidar firmas
 * mensuales. No es una decisión de quien lleva un equipo.
 */
@RestController
@RequestMapping("/api/v1/empresa/ajustes")
@Tag(name = "Empresa", description = "Ajustes de la empresa: nombre y zona horaria.")
@SecurityRequirement(name = "bearerAuth")
public class CompanySettingsController {

    private final CompanySettingsService service;

    public CompanySettingsController(CompanySettingsService service) {
        this.service = service;
    }

    @Operation(summary = "Ajustes de mi empresa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ajustes",
                    content = @Content(schema = @Schema(implementation = CompanySettingsResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empresa:configurar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<CompanySettingsResponse> leer(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.leer(usuario.getUser()));
    }

    @Operation(summary = "Guardar los ajustes de mi empresa",
            description = "Nombre y zona horaria (nombre IANA, p. ej. 'Atlantic/Canary'). Cambiar la zona "
                    + "cambia a qué día pertenece cada fichaje de todo el histórico: las firmas mensuales "
                    + "cuyo mes cambie de contenido se invalidan, y la respuesta dice cuántas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Guardados",
                    content = @Content(schema = @Schema(implementation = CompanySettingsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Nombre vacío o zona horaria desconocida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empresa:configurar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Otra empresa ya se llama así",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<CompanySettingsResponse> guardar(
            @Valid @RequestBody UpdateCompanySettingsRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.guardar(request, usuario.getUser()));
    }
}
