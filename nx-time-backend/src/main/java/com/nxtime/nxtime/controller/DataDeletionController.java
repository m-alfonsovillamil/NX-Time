package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.DeletionRequestDTO;
import com.nxtime.nxtime.dto.DeletionResponse;
import com.nxtime.nxtime.dto.RejectDeletionRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.DataDeletionService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Borrado de datos personales (RGPD, art. 17). Ver ADR 016.
 *
 * Dos lados en el mismo controlador para que el flujo se lea entero: lo que
 * hace la persona sobre lo suyo ({@code /perfil/borrado}, sin authority) y lo
 * que hace RRHH/ADMIN ({@code /borrados}, con {@code empleado:gestionar}).
 */
@RestController
@Tag(name = "Borrado de datos", description = "Derecho de supresión (RGPD, art. 17): la persona lo pide y "
        + "RRHH o ADMIN lo ejecuta. Ejecutar borra lo prescindible y desactiva la cuenta; el registro horario "
        + "se conserva cuatro años y después se anonimiza solo.")
@SecurityRequirement(name = "bearerAuth")
public class DataDeletionController {

    private final DataDeletionService service;

    public DataDeletionController(DataDeletionService service) {
        this.service = service;
    }

    @Operation(summary = "Pedir el borrado de mis datos",
            description = "El motivo es opcional. Avisa a RRHH y ADMIN, sin el motivo. No borra nada todavía: "
                    + "lo hace quien lo ejecute.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitud creada",
                    content = @Content(schema = @Schema(implementation = DeletionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Motivo demasiado largo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya hay una solicitud pendiente",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/api/v1/perfil/borrado")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeletionResponse> solicitar(
            @Valid @RequestBody(required = false) DeletionRequestDTO peticion,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.solicitar(usuario.getUser(), peticion));
    }

    @Operation(summary = "Mi última solicitud de borrado",
            description = "En cualquier estado, para que la persona vea en qué ha quedado. 204 si nunca pidió ninguna.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La última solicitud",
                    content = @Content(schema = @Schema(implementation = DeletionResponse.class))),
            @ApiResponse(responseCode = "204", description = "Nunca ha pedido ninguna"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/api/v1/perfil/borrado")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeletionResponse> miSolicitud(@AuthenticationPrincipal SecurityUser usuario) {
        return service.miUltimaSolicitud(usuario.getUser())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Retirar mi solicitud de borrado", description = "Solo mientras está pendiente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelada",
                    content = @Content(schema = @Schema(implementation = DeletionResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No hay ninguna pendiente",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/api/v1/perfil/borrado/cancelar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeletionResponse> cancelar(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.cancelar(usuario.getUser()));
    }

    @Operation(summary = "Solicitudes de borrado pendientes",
            description = "Las de la empresa, las más antiguas primero (hay un mes para responder). Cada una "
                    + "lleva en 'bloqueos' lo que hoy impide ejecutarla; vacía si se puede.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pendientes",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DeletionResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empleado:gestionar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/api/v1/borrados/pendientes")
    @PreAuthorize("hasAuthority('empleado:gestionar')")
    public ResponseEntity<List<DeletionResponse>> pendientes(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.pendientes(usuario.getUser()));
    }

    @Operation(summary = "Ejecutar un borrado",
            description = "IRREVERSIBLE. Borra CV, foto, fecha de nacimiento, candidaturas, avisos, sesiones y "
                    + "códigos de acceso, y desactiva la cuenta. Conserva nombre, correo y registro horario, que "
                    + "se anonimizan cuatro años después del último fichaje. Rechaza con 409 si queda algo "
                    + "abierto (jornada, correcciones, ausencias, denuncias), si es la propia o si es el único "
                    + "ADMIN activo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ejecutada",
                    content = @Content(schema = @Schema(implementation = DeletionResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority, o solicitud de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya resuelta, o con algo que lo impide",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/api/v1/borrados/{id}/ejecutar")
    @PreAuthorize("hasAuthority('empleado:gestionar')")
    public ResponseEntity<DeletionResponse> ejecutar(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.ejecutar(id, usuario.getUser()));
    }

    @Operation(summary = "Rechazar un borrado",
            description = "Con comentario obligatorio: se le manda a la persona para que sepa por qué y qué "
                    + "tiene que resolver antes de volver a pedirlo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rechazada",
                    content = @Content(schema = @Schema(implementation = DeletionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Sin comentario, o demasiado largo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority, o solicitud de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya resuelta, o es la propia",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/api/v1/borrados/{id}/rechazar")
    @PreAuthorize("hasAuthority('empleado:gestionar')")
    public ResponseEntity<DeletionResponse> rechazar(
            @PathVariable long id,
            @Valid @RequestBody RejectDeletionRequest peticion,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.rechazar(id, peticion.comentario(), usuario.getUser()));
    }
}
