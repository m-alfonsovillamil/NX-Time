package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.KioskDtos.ConfirmKioskRequest;
import com.nxtime.nxtime.dto.KioskDtos.KioskResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.KioskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los kioscos de la empresa, desde sus ajustes (ADR 033). Solo ADMIN, como el
 * resto de los ajustes: un kiosco ficha en nombre de cualquiera de la plantilla.
 */
@RestController
@RequestMapping("/api/v1/empresa/kioscos")
@Tag(name = "Empresa", description = "Ajustes de la empresa: nombre y zona horaria.")
@SecurityRequirement(name = "bearerAuth")
public class CompanyKioskController {

    private final KioskService service;

    public CompanyKioskController(KioskService service) {
        this.service = service;
    }

    @Operation(summary = "Los kioscos de mi empresa", description = "Los activos y los revocados, el último primero.")
    @ApiResponse(responseCode = "200", description = "Lista",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = KioskResponse.class))))
    @GetMapping
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<List<KioskResponse>> listar(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.listar(usuario.getUser()));
    }

    @Operation(summary = "Dar de alta un kiosco con el código de la tablet",
            description = "El código que enseña la tablet al abrir /kiosco. Caduca a los 10 minutos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dado de alta",
                    content = @Content(schema = @Schema(implementation = KioskResponse.class))),
            @ApiResponse(responseCode = "404", description = "El código no existe o ha caducado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<KioskResponse> confirmar(
            @Valid @RequestBody ConfirmKioskRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.confirmar(request, usuario.getUser()));
    }

    @Operation(summary = "Revocar un kiosco",
            description = "Deja de poder fichar desde la siguiente petición. No se borra: sus fichajes lo citan.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Revocado"),
            @ApiResponse(responseCode = "403", description = "De otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<Void> revocar(@PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        service.revocar(id, usuario.getUser());
        return ResponseEntity.noContent().build();
    }
}
