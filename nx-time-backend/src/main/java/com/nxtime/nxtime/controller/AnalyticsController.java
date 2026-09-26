package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.domain.AnalyticsGrouping;
import com.nxtime.nxtime.domain.AnalyticsPeriod;
import com.nxtime.nxtime.dto.AbsenteeismResponse;
import com.nxtime.nxtime.dto.AnalyticsSummaryResponse;
import com.nxtime.nxtime.dto.PunctualityResponse;
import com.nxtime.nxtime.report.AbsenteeismCsv;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analítica de absentismo y puntualidad (Fase B4, ADR 026).
 *
 * {@code analitica:leer} empieza en GESTOR. Lo que ve cada uno no lo decide la
 * authority sino el servicio: RRHH y ADMIN, la empresa; un GESTOR, su
 * departamento. El CSV pide además {@code informe:exportar}, como el resto de
 * exportaciones: un fichero sale del sistema y se reenvía.
 *
 * {@code /resumen} lo usan la app y la web; los otros tres son de la web, que
 * es donde cabe una tabla por departamento.
 */
@RestController
@RequestMapping("/api/v1/analitica")
@Tag(name = "Analítica", description = "Absentismo y puntualidad por periodo, departamento o persona.")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static final String DESCRIPCION_PERIODO = " El periodo es el mes, trimestre o año natural que contiene "
            + "'fecha' (hoy si no se da), y se cuenta hasta ayer. RRHH y ADMIN ven la empresa; un GESTOR, su "
            + "departamento.";

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Operation(summary = "Las cifras de un vistazo",
            description = "Absentismo, puntualidad, jornadas cerradas por el sistema y minutos medios por día."
                    + DESCRIPCION_PERIODO)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen",
                    content = @Content(schema = @Schema(implementation = AnalyticsSummaryResponse.class))),
            @ApiResponse(responseCode = "400", description = "Periodo o fecha no válidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'analitica:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Un gestor sin departamento asignado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('analitica:leer')")
    public ResponseEntity<AnalyticsSummaryResponse> resumen(
            @RequestParam(defaultValue = "MES") AnalyticsPeriod periodo,
            @RequestParam(required = false) LocalDate fecha,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(analyticsService.resumen(usuario.getUser(), periodo, oHoy(fecha)));
    }

    @Operation(summary = "Absentismo",
            description = "Días perdidos sobre días que se debían trabajar, sin contar vacaciones, con el "
                    + "desglose por motivo. Por persona, por departamento o solo el total." + DESCRIPCION_PERIODO)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Absentismo",
                    content = @Content(schema = @Schema(implementation = AbsenteeismResponse.class))),
            @ApiResponse(responseCode = "400", description = "Periodo, fecha o agrupación no válidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'analitica:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Un gestor sin departamento asignado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/absentismo")
    @PreAuthorize("hasAuthority('analitica:leer')")
    public ResponseEntity<AbsenteeismResponse> absentismo(
            @RequestParam(defaultValue = "MES") AnalyticsPeriod periodo,
            @RequestParam(required = false) LocalDate fecha,
            @RequestParam(defaultValue = "DEPARTAMENTO") AnalyticsGrouping agrupar,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(analyticsService.absentismo(usuario.getUser(), periodo, oHoy(fecha), agrupar));
    }

    @Operation(summary = "Puntualidad",
            description = "De las entradas de quien tiene cuadrante, cuántas llegaron dentro de la tolerancia, y "
                    + "la media, la mediana y el reparto de los retrasos." + DESCRIPCION_PERIODO)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Puntualidad",
                    content = @Content(schema = @Schema(implementation = PunctualityResponse.class))),
            @ApiResponse(responseCode = "400", description = "Periodo, fecha o agrupación no válidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'analitica:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Un gestor sin departamento asignado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/puntualidad")
    @PreAuthorize("hasAuthority('analitica:leer')")
    public ResponseEntity<PunctualityResponse> puntualidad(
            @RequestParam(defaultValue = "MES") AnalyticsPeriod periodo,
            @RequestParam(required = false) LocalDate fecha,
            @RequestParam(defaultValue = "DEPARTAMENTO") AnalyticsGrouping agrupar,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(analyticsService.puntualidad(usuario.getUser(), periodo, oHoy(fecha), agrupar));
    }

    @Operation(summary = "Absentismo en CSV",
            description = "Lo mismo que /absentismo, para una hoja de cálculo: separado por punto y coma, con coma "
                    + "decimal y en UTF-8 con BOM. Una fila por grupo y el total al final." + DESCRIPCION_PERIODO)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El fichero",
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "403", description = "Sin 'analitica:leer' o sin 'informe:exportar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/absentismo.csv")
    @PreAuthorize("hasAuthority('analitica:leer') and hasAuthority('informe:exportar')")
    public ResponseEntity<byte[]> absentismoCsv(
            @RequestParam(defaultValue = "MES") AnalyticsPeriod periodo,
            @Parameter(description = "Cualquier día del periodo; hoy si no se da")
            @RequestParam(required = false) LocalDate fecha,
            @RequestParam(defaultValue = "DEPARTAMENTO") AnalyticsGrouping agrupar,
            @AuthenticationPrincipal SecurityUser usuario) {
        AbsenteeismResponse absentismo =
                analyticsService.absentismo(usuario.getUser(), periodo, oHoy(fecha), agrupar);
        String nombre = "absentismo-" + absentismo.ventana().desde() + "-a-" + absentismo.ventana().hasta() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(AbsenteeismCsv.generar(absentismo));
    }

    private static LocalDate oHoy(LocalDate fecha) {
        return fecha != null ? fecha : LocalDate.now(MADRID);
    }
}
