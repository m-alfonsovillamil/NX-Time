package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.DataAccessResponse;
import com.nxtime.nxtime.dto.PrivacyNoticeResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.impl.AvisoDePrivacidad;
import com.nxtime.nxtime.service.impl.RegistroDeAccesos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El RGPD dentro del producto (plan J, fase J5; ADR 047): el aviso de la
 * primera entrada y el registro de quién ha consultado datos ajenos.
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Privacidad", description = "El aviso de privacidad y el registro de accesos a datos personales.")
public class PrivacyController {

    private final AvisoDePrivacidad aviso;
    private final RegistroDeAccesos accesos;

    public PrivacyController(AvisoDePrivacidad aviso, RegistroDeAccesos accesos) {
        this.aviso = aviso;
        this.accesos = accesos;
    }

    @Operation(summary = "Mi aviso de privacidad",
            description = "Quién es la responsable de mis datos (mi empresa), a quién dirigirme y si ya lo he visto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El aviso",
                    content = @Content(schema = @Schema(implementation = PrivacyNoticeResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/api/v1/perfil/aviso-de-privacidad")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PrivacyNoticeResponse> leerAviso(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(aviso.leer(usuario.getUser()));
    }

    @Operation(summary = "Ya he visto el aviso de privacidad",
            description = "Solo cuenta la primera vez: repetirlo no cambia la fecha.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Anotado"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/api/v1/perfil/aviso-de-privacidad/visto")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> marcarAvisoVisto(@AuthenticationPrincipal SecurityUser usuario) {
        aviso.marcarVisto(usuario.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Quién ha consultado datos personales de otra persona",
            description = "Los CV de candidaturas, perfiles ajenos, registros mensuales de otro, el Excel de la "
                    + "empresa, expedientes de denuncias y exportaciones. Lo último primero, hasta 500. Se "
                    + "conserva dos años y no se puede borrar ni cambiar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Los accesos",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DataAccessResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Sin 'empresa:configurar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/api/v1/empresa/accesos")
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<List<DataAccessResponse>> accesos(
            @Parameter(description = "Solo los datos de esta persona") @RequestParam(required = false) Long usuarioId,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(accesos.deLaEmpresa(usuario.getUser(), usuarioId));
    }
}
