package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.CompanyTermsResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.CompanyLifecycleService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las condiciones del servicio y el contrato de encargo que tiene aceptados la
 * empresa (ADR 046). Solo el ADMIN ({@code empresa:configurar}): aceptarlas
 * compromete a la empresa.
 */
@RestController
@RequestMapping("/api/v1/empresa/condiciones")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Empresa", description = "La configuración de la empresa")
public class CompanyTermsController {

    private final CompanyLifecycleService cicloDeLaEmpresa;

    public CompanyTermsController(CompanyLifecycleService cicloDeLaEmpresa) {
        this.cicloDeLaEmpresa = cicloDeLaEmpresa;
    }

    @Operation(summary = "Las condiciones de mi empresa",
            description = "La versión en vigor, la que tiene aceptada la empresa, cuándo y quién.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Las condiciones",
                    content = @Content(schema = @Schema(implementation = CompanyTermsResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'empresa:configurar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<CompanyTermsResponse> condiciones(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(cicloDeLaEmpresa.condiciones(usuario.getUser()));
    }

    @Operation(summary = "Aceptar las condiciones en vigor",
            description = "En nombre de la empresa. Queda la versión, la fecha y quién.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aceptadas",
                    content = @Content(schema = @Schema(implementation = CompanyTermsResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'empresa:configurar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/aceptar")
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<CompanyTermsResponse> aceptar(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(cicloDeLaEmpresa.aceptarCondiciones(usuario.getUser()));
    }
}
