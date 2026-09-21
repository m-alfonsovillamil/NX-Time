package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.audit.VerificadorDeAuditoria;
import com.nxtime.nxtime.dto.AuditCheckpointResponse;
import com.nxtime.nxtime.dto.AuditIntegrityResponse;
import com.nxtime.nxtime.dto.TimeEntryAuditResponse;
import com.nxtime.nxtime.mapper.TimeEntryAuditMapper;
import com.nxtime.nxtime.service.TimeEntryService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la auditoría inalterable de fichajes (Fase 8 del plan de
 * profesionalización). Ver {@link com.nxtime.nxtime.domain.TimeEntryAudit}
 * y {@link com.nxtime.nxtime.audit.TimeEntryAuditListener} para cómo se
 * escribe -- este controlador solo lee.
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@Tag(name = "Auditoría", description = "Línea temporal de cambios de un fichaje (RD-ley 8/2019). Solo lectura.")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final TimeEntryService timeEntryService;
    private final TimeEntryAuditMapper auditMapper;
    private final VerificadorDeAuditoria verificador;

    public AuditController(
            TimeEntryService timeEntryService,
            TimeEntryAuditMapper auditMapper,
            VerificadorDeAuditoria verificador) {
        this.timeEntryService = timeEntryService;
        this.auditMapper = auditMapper;
        this.verificador = verificador;
    }

    @Operation(summary = "Línea temporal de un fichaje",
            description = "Todas las entradas de auditoría de ese fichaje, más antigua primero: su creación, "
                    + "sus modificaciones (fin de jornada, pausas) y, si la hubo, su corrección.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Línea temporal (vacía si el fichaje no tiene cambios registrados)",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TimeEntryAuditResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:auditoria', "
                    + "o fichaje de otra empresa (aislamiento multi-tenant)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Fichaje no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:auditoria')")
    @GetMapping("/fichaje/{id}")
    public ResponseEntity<List<TimeEntryAuditResponse>> getAuditTrail(@PathVariable long id, Authentication authentication) {
        var trail = timeEntryService.getAuditTrail(authentication.getName(), id).stream()
                .map(auditMapper::toResponse)
                .toList();
        return ResponseEntity.ok(trail);
    }

    @Operation(summary = "Comprobar que la traza no se ha manipulado",
            description = "Recorre la cadena de hashes y dice si sigue intacta. Es la respuesta a "
                    + "\"demuéstrame que este registro no se ha tocado\": el RD-ley 8/2019 exige "
                    + "conservarlo cuatro años y que sea fiable, y una cadena que nadie comprueba nunca "
                    + "no demuestra nada. Los movimientos anteriores a septiembre de 2026 solo admiten "
                    + "la comprobación del enlace con el anterior, y se cuentan aparte.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado de la comprobación, intacta o no",
                    content = @Content(schema = @Schema(implementation = AuditIntegrityResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:auditoria'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:auditoria')")
    @GetMapping("/integridad")
    public ResponseEntity<AuditIntegrityResponse> verificarIntegridad() {
        // Sin parámetros a propósito: la cadena enlaza TODAS las filas de la
        // tabla, sean de la empresa que sean, así que comprobar un trozo
        // suelto daría un enlace roto en cada borde. O se comprueba entera, o
        // no se comprueba.
        //
        // Desde la Fase A4 se recorre por bloques en vez de traérsela entera a
        // memoria, así que sigue siendo la comprobación completa pero ya no
        // crece sin techo con los años de traza.
        return ResponseEntity.ok(verificador.verificar());
    }

    @Operation(summary = "Cuándo se comprobó la traza por última vez",
            description = "La verificación completa cuesta, y lo que costaba se pedía poco. Desde "
                    + "septiembre de 2026 una tarea nocturna la comprueba sola y deja un punto de "
                    + "control; esto lo devuelve, para poder decir \"comprobada anoche, intacta\" sin "
                    + "volver a recorrer nada. Devuelve 204 mientras no se haya comprobado ninguna vez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La última comprobación automática",
                    content = @Content(schema = @Schema(implementation = AuditCheckpointResponse.class))),
            @ApiResponse(responseCode = "204", description = "Todavía no se ha comprobado nunca"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:auditoria'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:auditoria')")
    @GetMapping("/integridad/ultima")
    public ResponseEntity<AuditCheckpointResponse> ultimaComprobacion() {
        return verificador.ultimoPuntoDeControl()
                .map(punto -> ResponseEntity.ok(new AuditCheckpointResponse(
                        punto.getVerificadoEn(),
                        punto.getHastaId(),
                        punto.getFilas(),
                        punto.getComprobadas(),
                        punto.getSoloEnlace(),
                        verificador.movimientosSinRevisar(punto))))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
