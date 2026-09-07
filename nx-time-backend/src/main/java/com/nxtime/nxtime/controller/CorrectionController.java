package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.DisputeRequest;
import com.nxtime.nxtime.dto.ResolveCorrectionRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.CorrectionService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resolver solicitudes de corrección (Fase E). Pedirlas se hace desde el
 * fichaje: {@code POST /api/v1/fichaje/{id}/correcciones}.
 *
 * <b>Quién puede resolver cada solicitud no lo dice el rol, lo dice la
 * propia solicitud</b>, porque depende de quién la pidió. Por eso los
 * endpoints de resolver y disputar llevan una authority amplia
 * ({@code correccion:solicitar}, que tiene todo el mundo) y el permiso
 * real lo comprueba el servicio: un empleado sin ningún privilegio
 * <b>tiene</b> que poder aprobar o disputar la corrección que le han
 * propuesto sobre su propio fichaje. Ponerle
 * {@code hasAuthority('correccion:aprobar')} al endpoint dejaría fuera
 * justo a quien más derecho tiene a decidir.
 */
@RestController
@RequestMapping("/api/v1/correcciones")
@Tag(name = "Correcciones", description = "Solicitudes de corrección de fichajes y su resolución.")
@SecurityRequirement(name = "bearerAuth")
public class CorrectionController {

    private final CorrectionService correctionService;

    public CorrectionController(CorrectionService correctionService) {
        this.correctionService = correctionService;
    }

    @Operation(summary = "Las correcciones que me toca resolver a mí",
            description = "Incluye tanto las de mi equipo (si puedo aprobarlas) como las que otra "
                    + "persona ha pedido sobre MIS fichajes, y las disputas si soy quien las "
                    + "resuelve. Es una sola lista porque para quien la mira es una sola cosa: "
                    + "lo que está esperando por él.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CorrectionResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/pendientes")
    @PreAuthorize("hasAuthority('correccion:solicitar')")
    public ResponseEntity<List<CorrectionResponse>> pendientes(
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(correctionService.pendientesParaMi(usuario.getUser()));
    }

    @Operation(summary = "Las correcciones que he pedido yo",
            description = "Con su estado, para ver en qué han quedado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CorrectionResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/mias")
    @PreAuthorize("hasAuthority('correccion:solicitar')")
    public ResponseEntity<List<CorrectionResponse>> mias(
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(correctionService.mias(usuario.getUser()));
    }

    @Operation(summary = "Aprobar o rechazar una corrección",
            description = "Aprobar es lo que APLICA la corrección: anula el fichaje original y crea "
                    + "el corregido. Al rechazar, el comentario es obligatorio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resuelta",
                    content = @Content(schema = @Schema(implementation = CorrectionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Falta 'aprobada', o rechazo sin comentario",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "No te toca a ti resolverla, o es de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Solicitud no encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya estaba resuelta, o el fichaje se corrigió por otra vía",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('correccion:solicitar')")
    public ResponseEntity<CorrectionResponse> resolver(
            @PathVariable long id,
            @Valid @RequestBody ResolveCorrectionRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(correctionService.resolver(id, request, usuario.getUser()));
    }

    @Operation(summary = "No aceptar una corrección que me han propuesto",
            description = "Solo el dueño del fichaje, y solo sobre una corrección que ha pedido otra "
                    + "persona. No la rechaza: la escala a RRHH, que decide en firme. Rechazarla "
                    + "sería que una de las dos partes se dé la razón a sí misma.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "En disputa",
                    content = @Content(schema = @Schema(implementation = CorrectionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Sin motivo, o es una corrección que pediste tú",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "No eres el dueño del fichaje",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Solicitud no encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "La solicitud ya no está pendiente",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/disputa")
    @PreAuthorize("hasAuthority('correccion:solicitar')")
    public ResponseEntity<CorrectionResponse> disputar(
            @PathVariable long id,
            @Valid @RequestBody DisputeRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(correctionService.disputar(id, request, usuario.getUser()));
    }
}
