package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.report.ExcelReportGenerator;
import com.nxtime.nxtime.report.MonthlyReport;
import com.nxtime.nxtime.report.PdfReportGenerator;
import com.nxtime.nxtime.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.YearMonth;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Descarga de informes (Fase 10).
 *
 * Los dos endpoints devuelven {@link StreamingResponseBody}: el
 * documento se escribe directamente en la respuesta según se genera, en
 * vez de construir un {@code byte[]} completo en memoria y devolverlo.
 * Para un informe mensual la diferencia es pequeña, pero es el patrón
 * correcto y evita tener que rehacerlo el día que alguien pida el
 * histórico de un año.
 *
 * Nota sobre errores: una vez empieza a escribirse el cuerpo ya se ha
 * enviado el 200 y las cabeceras, así que un fallo a mitad NO se puede
 * convertir en un ProblemDetail. Por eso todo lo que puede fallar
 * -- usuario inexistente, empleado de otra empresa -- se comprueba
 * ANTES, al preparar el informe, y no dentro del lambda.
 */
@RestController
@RequestMapping("/api/v1/informes")
@Validated
@Tag(name = "Informes", description = "Exportación de registros horarios en Excel y PDF (RD-ley 8/2019).")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;
    private final ExcelReportGenerator excelGenerator;
    private final PdfReportGenerator pdfGenerator;

    public ReportController(
            ReportService reportService,
            ExcelReportGenerator excelGenerator,
            PdfReportGenerator pdfGenerator
    ) {
        this.reportService = reportService;
        this.excelGenerator = excelGenerator;
        this.pdfGenerator = pdfGenerator;
    }

    @Operation(summary = "Excel de horas de la empresa",
            description = "Todas las jornadas cerradas de la empresa en el mes indicado, con sus totales. "
                    + "Las jornadas cerradas automáticamente por el sistema aparecen marcadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Hoja de cálculo .xlsx",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            @ApiResponse(responseCode = "400", description = "Mes o año fuera de rango",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'informe:exportar' (RRHH/ADMIN)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('informe:exportar')")
    @GetMapping("/horas")
    public ResponseEntity<StreamingResponseBody> exportarHorasEnExcel(
            @Parameter(description = "Año del informe") @RequestParam @Min(2000) @Max(2100) int anio,
            @Parameter(description = "Mes del informe (1-12)") @RequestParam @Min(1) @Max(12) int mes,
            Authentication authentication) {

        // Se prepara ANTES de empezar a escribir: ver el Javadoc.
        MonthlyReport informe = reportService.informeDeEmpresa(authentication.getName(), YearMonth.of(anio, mes));

        StreamingResponseBody cuerpo = salida -> excelGenerator.generar(informe, salida);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"horas-" + YearMonth.of(anio, mes) + ".xlsx\"")
                .body(cuerpo);
    }

    @Operation(summary = "Informe mensual de un empleado en PDF",
            description = "El registro de jornada individual que exige el RD-ley 8/2019, con el detalle diario, "
                    + "el total del mes y espacio para las firmas del trabajador y de la empresa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento PDF",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "400", description = "Mes o año fuera de rango",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin authority, o empleado de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('informe:exportar')")
    @GetMapping("/mensual/{empleadoId}")
    public ResponseEntity<StreamingResponseBody> exportarInformeMensualEnPdf(
            @PathVariable long empleadoId,
            @Parameter(description = "Año del informe") @RequestParam @Min(2000) @Max(2100) int anio,
            @Parameter(description = "Mes del informe (1-12)") @RequestParam @Min(1) @Max(12) int mes,
            Authentication authentication) {

        MonthlyReport informe = reportService.informeDeEmpleado(
                authentication.getName(), empleadoId, YearMonth.of(anio, mes));

        StreamingResponseBody cuerpo = salida -> pdfGenerator.generar(informe, salida);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"registro-" + empleadoId + "-" + YearMonth.of(anio, mes) + ".pdf\"")
                .body(cuerpo);
    }
}
