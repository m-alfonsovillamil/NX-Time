package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.OvertimeAlertResponse;
import com.nxtime.nxtime.dto.OvertimeBalanceResponse;
import com.nxtime.nxtime.dto.ReviewOvertimeRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.OvertimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Horas extra: avisos de exceso de jornada y bolsa anual (Fase F).
 *
 * Los detecta el proceso nocturno ({@code OvertimeScheduler}); aquí solo
 * se consultan y se revisan. No hay endpoint para crear un aviso a mano
 * ni para lanzar el barrido: un exceso de jornada es un hecho medido, no
 * algo que se declare.
 *
 * <b>Ver lo tuyo no pide authority; revisarlo, sí.</b> Mirar tus propias
 * horas extra es mirar tu propia jornada, así que basta con estar
 * autenticado — de hecho el aviso te llega a ti antes que a nadie,
 * porque eres quien sabe si aquel martes fue una intensiva pactada. Lo
 * que pide {@code horasextra:revisar} es DECIDIR, y ni siquiera esa
 * authority permite decidir sobre lo propio: eso lo corta el servicio.
 */
@RestController
@RequestMapping("/api/v1/horas-extra")
@Tag(name = "Horas extra", description = "Excesos de jornada detectados y bolsa anual del art. 35.2 ET.")
@SecurityRequirement(name = "bearerAuth")
public class OvertimeController {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final OvertimeService overtimeService;

    public OvertimeController(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    @Operation(summary = "Mis avisos de horas extra",
            description = "Los excesos detectados en mis fichajes durante el año indicado, "
                    + "revisados y sin revisar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = OvertimeAlertResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('fichaje:leer')")
    public ResponseEntity<List<OvertimeAlertResponse>> mios(
            @RequestParam(required = false) Integer anio,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(overtimeService.mios(usuario.getUser(), anioOEsteAnio(anio)));
    }

    @Operation(summary = "Los avisos de toda la empresa",
            description = "La bandeja de quien revisa. Los ABIERTO salen primero: son los que "
                    + "están esperando una decisión.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = OvertimeAlertResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Sin permiso para revisar horas extra",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/equipo")
    @PreAuthorize("hasAuthority('horasextra:revisar')")
    public ResponseEntity<List<OvertimeAlertResponse>> delEquipo(
            @RequestParam(required = false) Integer anio,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(overtimeService.delEquipo(usuario.getUser(), anioOEsteAnio(anio)));
    }

    @Operation(summary = "Decidir si un exceso cuenta como horas extra",
            description = "Aceptarlo lo hace descontar de la bolsa anual de 80 h. Justificarlo lo "
                    + "archiva sin consumir bolsa, y entonces la explicación es obligatoria: decir "
                    + "que once horas trabajadas no cuentan es la decisión que hay que motivar. "
                    + "Nadie revisa las suyas propias, tenga el rol que tenga.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revisado",
                    content = @Content(schema = @Schema(implementation = OvertimeAlertResponse.class))),
            @ApiResponse(responseCode = "400", description = "Falta 'aceptar', o se justifica sin explicación",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Es de otra empresa, o son tus propias horas",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Aviso no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya estaba revisado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('horasextra:revisar')")
    public ResponseEntity<OvertimeAlertResponse> revisar(
            @PathVariable long id,
            @Valid @RequestBody ReviewOvertimeRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(overtimeService.revisar(id, request, usuario.getUser()));
    }

    @Operation(summary = "La bolsa anual de horas extra",
            description = "Las 80 h del art. 35.2 ET y lo que va consumido. Sin 'usuarioId' "
                    + "devuelve la propia; con él, la de otra persona, y eso sí pide "
                    + "'horasextra:revisar'. No hay contador guardado: se suma al leer, para que "
                    + "una corrección de un fichaje pasado no lo deje mintiendo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bolsa",
                    content = @Content(schema = @Schema(implementation = OvertimeBalanceResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bolsa de otra persona sin permiso, o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/bolsa")
    @PreAuthorize("hasAuthority('fichaje:leer')")
    public ResponseEntity<OvertimeBalanceResponse> bolsa(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) Integer anio,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(
                overtimeService.bolsa(usuario.getUser(), usuarioId, anioOEsteAnio(anio)));
    }

    /**
     * El año por defecto es el de hoy EN MADRID, no el del reloj del
     * servidor: el 31 de diciembre a las 23:30 en España ya es 1 de enero
     * en UTC, y la bolsa anual se vaciaría media hora antes de tiempo.
     */
    private int anioOEsteAnio(Integer anio) {
        return anio != null ? anio : LocalDate.now(MADRID).getYear();
    }
}
