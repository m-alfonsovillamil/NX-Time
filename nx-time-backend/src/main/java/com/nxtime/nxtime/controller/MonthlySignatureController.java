package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.MonthlySignatureResponse;
import com.nxtime.nxtime.dto.SignMonthRequest;
import com.nxtime.nxtime.dto.SignableMonthResponse;
import com.nxtime.nxtime.dto.SignatureVerificationResponse;
import com.nxtime.nxtime.dto.TeamSignatureResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.MonthlySignatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.YearMonth;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La firma mensual del registro horario (Fase B3, ADR 025).
 *
 * Firmar lo propio no pide authority nueva: es tu registro. Ver cómo está la
 * empresa y visar es {@code firma:visar} (RRHH), y ni con ella se visa la
 * propia firma (lo corta el servicio).
 */
@RestController
@RequestMapping("/api/v1/firmas")
@Validated
@Tag(name = "Firma mensual", description = "La persona firma que su registro de un mes es correcto. Firma de "
        + "aceptación, no eIDAS. Una corrección posterior no se bloquea: invalida la firma.")
@SecurityRequirement(name = "bearerAuth")
public class MonthlySignatureController {

    private final MonthlySignatureService signatureService;

    public MonthlySignatureController(MonthlySignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @Operation(summary = "Mis meses por firmar",
            description = "Los últimos seis meses terminados en los que hay algo: si se pueden firmar, por qué no, "
                    + "y la firma vigente o la última que se invalidó.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Meses",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = SignableMonthResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/mias")
    @PreAuthorize("hasAuthority('fichaje:leer')")
    public ResponseEntity<List<SignableMonthResponse>> misMeses(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(signatureService.misMeses(usuario.getUser()));
    }

    @Operation(summary = "Firmar un mes propio",
            description = "Guarda la huella SHA-256 del resumen del mes. El mes tiene que haber terminado y no puede "
                    + "tener jornadas abiertas ni cerradas por el sistema sin corregir.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Firmado",
                    content = @Content(schema = @Schema(implementation = MonthlySignatureResponse.class))),
            @ApiResponse(responseCode = "400", description = "El mes no ha terminado, o fecha no válida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ese mes ya está firmado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Jornadas abiertas o cerradas por el sistema, o ninguna",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('fichaje:leer')")
    public ResponseEntity<MonthlySignatureResponse> firmar(
            @Valid @RequestBody SignMonthRequest request,
            @AuthenticationPrincipal SecurityUser usuario,
            HttpServletRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(signatureService.firmar(
                usuario.getUser(), YearMonth.of(request.anio(), request.mes()), peticion.getRemoteAddr()));
    }

    @Operation(summary = "Comprobar una firma",
            description = "Recalcula hoy la huella del mes y la compara con la firmada. La propia, o cualquiera de la "
                    + "empresa con firma:visar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado",
                    content = @Content(schema = @Schema(implementation = SignatureVerificationResponse.class))),
            @ApiResponse(responseCode = "403", description = "Ni es tuya ni puedes visar, o es de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}/verificacion")
    @PreAuthorize("hasAuthority('fichaje:leer')")
    public ResponseEntity<SignatureVerificationResponse> verificar(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(signatureService.verificar(id, usuario.getUser()));
    }

    @Operation(summary = "Cómo está un mes en la empresa",
            description = "Cada persona activa, con su firma vigente, la última invalidada o ninguna.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Personas",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TeamSignatureResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Sin 'firma:visar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/equipo")
    @PreAuthorize("hasAuthority('firma:visar')")
    public ResponseEntity<List<TeamSignatureResponse>> equipo(
            @RequestParam @Min(2020) @Max(2100) int anio,
            @RequestParam @Min(1) @Max(12) int mes,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(signatureService.equipo(usuario.getUser(), YearMonth.of(anio, mes)));
    }

    @Operation(summary = "Visar una firma",
            description = "El visto bueno de la empresa a una firma vigente. Nunca a la propia.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visada",
                    content = @Content(schema = @Schema(implementation = MonthlySignatureResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin el permiso, es tuya, o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya no está vigente, o ya está visada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/visado")
    @PreAuthorize("hasAuthority('firma:visar')")
    public ResponseEntity<MonthlySignatureResponse> visar(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(signatureService.visar(id, usuario.getUser()));
    }
}
