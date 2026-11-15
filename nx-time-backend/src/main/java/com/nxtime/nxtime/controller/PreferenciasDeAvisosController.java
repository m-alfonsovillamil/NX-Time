package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.PreferenciasDeAvisos.GuardarPreferenciasRequest;
import com.nxtime.nxtime.dto.PreferenciasDeAvisos.PreferenciaDeAviso;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.PreferenciasDeAvisosService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cómo le llegan los avisos a cada persona (ADR 059; plan N, paso N9). Es de
 * uno mismo, como el resto de {@code /perfil}: cualquiera con sesión.
 */
@RestController
@RequestMapping("/api/v1/perfil/avisos")
@Tag(name = "Perfil")
@SecurityRequirement(name = "bearerAuth")
public class PreferenciasDeAvisosController {

    private final PreferenciasDeAvisosService preferencias;

    public PreferenciasDeAvisosController(PreferenciasDeAvisosService preferencias) {
        this.preferencias = preferencias;
    }

    @Operation(summary = "Cómo me llegan los avisos",
            description = "Por categoría, si llegan por correo y al móvil. La campana de la aplicación los recibe "
                    + "siempre. Las obligatorias (cuenta, firma, denuncias, borrado de datos) no se pueden apagar.")
    @ApiResponse(responseCode = "200", description = "Todas las categorías",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PreferenciaDeAviso.class))))
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<PreferenciaDeAviso>> misPreferenciasDeAvisos(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(preferencias.mias(usuario.getUser()));
    }

    @Operation(summary = "Cambiar cómo me llegan los avisos",
            description = "Solo las categorías que vienen; las demás se quedan como estaban. Devuelve todas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Todas las categorías, ya guardadas",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PreferenciaDeAviso.class)))),
            @ApiResponse(responseCode = "400", description = "Una categoría obligatoria, o el cuerpo mal formado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("isAuthenticated()")
    @PutMapping
    public ResponseEntity<List<PreferenciaDeAviso>> guardarPreferenciasDeAvisos(
            @Valid @RequestBody GuardarPreferenciasRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(preferencias.guardar(usuario.getUser(), request.preferencias()));
    }
}
