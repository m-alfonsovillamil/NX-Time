package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.CompanyClosureRequest;
import com.nxtime.nxtime.dto.CompanyClosureResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.impl.BajaDeEmpresa;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La baja de la empresa (condiciones, punto 9; ADR 050). Solo el ADMIN
 * ({@code empresa:configurar}): compromete a la empresa entera.
 */
@RestController
@RequestMapping("/api/v1/empresa/baja")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Empresa", description = "La configuración de la empresa")
public class CompanyClosureController {

    private final BajaDeEmpresa baja;

    public CompanyClosureController(BajaDeEmpresa baja) {
        this.baja = baja;
    }

    @Operation(summary = "La baja de mi empresa", description = "Si está pedida, cuándo y desde cuándo se borra.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La baja",
                    content = @Content(schema = @Schema(implementation = CompanyClosureResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'empresa:configurar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<CompanyClosureResponse> estado(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(baja.estado(usuario.getUser()));
    }

    @Operation(summary = "Pedir la baja de mi empresa",
            description = "Hay que escribir el nombre de la empresa. Durante 30 días todo sigue funcionando para "
                    + "llevarse los datos, y se puede cancelar; después se borra entera, auditoría incluida, y no "
                    + "se puede deshacer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedida",
                    content = @Content(schema = @Schema(implementation = CompanyClosureResponse.class))),
            @ApiResponse(responseCode = "400", description = "El nombre no coincide",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya estaba pedida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<CompanyClosureResponse> solicitar(
            @Valid @RequestBody CompanyClosureRequest request, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(baja.solicitar(usuario.getUser(), request.confirmacion()));
    }

    @Operation(summary = "Cancelar la baja", description = "Mientras no hayan pasado los 30 días.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelada",
                    content = @Content(schema = @Schema(implementation = CompanyClosureResponse.class))),
            @ApiResponse(responseCode = "409", description = "No había ninguna",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<CompanyClosureResponse> cancelar(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(baja.cancelar(usuario.getUser()));
    }
}
