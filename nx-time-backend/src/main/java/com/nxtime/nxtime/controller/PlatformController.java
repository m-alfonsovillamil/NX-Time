package com.nxtime.nxtime.controller;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import com.nxtime.nxtime.service.CompanyLifecycleService;
import com.nxtime.nxtime.dto.PlatformActionRequest;
import com.nxtime.nxtime.audit.VerificadorDeAuditoria;
import com.nxtime.nxtime.domain.PlatformOrder;
import com.nxtime.nxtime.dto.PaginaDTO;
import com.nxtime.nxtime.dto.PlatformCompanyDetailResponse;
import com.nxtime.nxtime.dto.PlatformCompanyResponse;
import com.nxtime.nxtime.dto.PlatformIntegrityResponse;
import com.nxtime.nxtime.dto.PlatformSummaryResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.Paginacion;
import com.nxtime.nxtime.service.PlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El panel de plataforma (ADR 040): qué empresas hay dadas de alta y cuánto
 * usan el servicio.
 *
 * <b>Todo aquí pide {@code plataforma:ver}, que no es de ningún rol</b>: un
 * ADMIN de cualquier empresa recibe 403 en todas estas rutas. La tienen las
 * cuentas de {@code PLATAFORMA_OPERADORES}; ver {@code OperadoresDePlataforma}.
 *
 * <b>Solo cifras</b>, más el contacto de los ADMIN de cada empresa. Desde el
 * ADR 046 también se aprueban, rechazan, suspenden y reactivan empresas, con
 * {@code plataforma:gestionar}; cada acción y cada consulta del detalle queda
 * en {@code acciones_de_plataforma}.
 */
@RestController
@RequestMapping("/api/v1/plataforma")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Plataforma", description = "La instalación entera, para quien la mantiene: cifras y el alta de "
        + "las empresas. No la abre ningún rol de empresa.")
public class PlatformController {

    /** Menos que en el resto de listas: cada fila lleva sus cifras y la pantalla es una tabla. */
    private static final String TAMANO_POR_DEFECTO = "25";

    private final PlatformService platformService;
    private final VerificadorDeAuditoria verificador;
    private final CompanyLifecycleService cicloDeLaEmpresa;

    public PlatformController(PlatformService platformService, VerificadorDeAuditoria verificador,
            CompanyLifecycleService cicloDeLaEmpresa) {
        this.platformService = platformService;
        this.verificador = verificador;
        this.cicloDeLaEmpresa = cicloDeLaEmpresa;
    }

    @Operation(summary = "La instalación de un vistazo",
            description = "Empresas y cuentas en total, altas de empresas por semana, fichajes de hoy, si las "
                    + "tareas nocturnas han corrido y qué dejó dicho la última comprobación de la traza de auditoría.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen",
                    content = @Content(schema = @Schema(implementation = PlatformSummaryResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'plataforma:ver'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/resumen")
    @PreAuthorize("hasAuthority('plataforma:ver')")
    public ResponseEntity<PlatformSummaryResponse> resumenDeLaInstalacion() {
        return ResponseEntity.ok(platformService.resumen());
    }

    @Operation(summary = "Las empresas dadas de alta",
            description = "Paginada, con las cifras de uso de cada una: plantilla, fichajes de los últimos "
                    + "7 y 30 días, cuánta gente ficha y la última vez que alguien entró.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Una página de empresas"),
            @ApiResponse(responseCode = "400", description = "Página, tamaño u orden no válidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'plataforma:ver'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/empresas")
    @PreAuthorize("hasAuthority('plataforma:ver')")
    public ResponseEntity<PaginaDTO<PlatformCompanyResponse>> empresas(
            @Parameter(description = "Parte del nombre, sin distinguir mayúsculas")
            @RequestParam(required = false) String busqueda,
            @RequestParam(defaultValue = "NOMBRE") PlatformOrder orden,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = TAMANO_POR_DEFECTO) int tamano) {
        return ResponseEntity.ok(platformService.empresas(busqueda, orden, Paginacion.pedir(pagina, tamano)));
    }

    @Operation(summary = "Una empresa",
            description = "Sus cifras y el contacto de sus ADMIN. Ni fichajes ni datos de empleados. "
                    + "Cada consulta queda en su historial, con quién la hizo (ADR 046).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La empresa",
                    content = @Content(schema = @Schema(implementation = PlatformCompanyDetailResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'plataforma:ver'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/empresas/{id}")
    @PreAuthorize("hasAuthority('plataforma:ver')")
    public ResponseEntity<PlatformCompanyDetailResponse> empresa(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(platformService.empresa(id, usuario.getUser()));
    }

    // ------------------------------------------------------------------
    // Aprobar, rechazar, suspender y reactivar (ADR 046)
    // ------------------------------------------------------------------

    @Operation(summary = "Aprobar una empresa pendiente",
            description = "Desde ese momento su gente puede entrar. Se avisa por correo a sus ADMIN, y queda en "
                    + "el historial de la empresa.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Aprobada"),
            @ApiResponse(responseCode = "403", description = "Sin 'plataforma:gestionar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "No está pendiente",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/empresas/{id}/aprobar")
    @PreAuthorize("hasAuthority('plataforma:gestionar')")
    public ResponseEntity<Void> aprobar(@PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        cicloDeLaEmpresa.aprobar(id, usuario.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Rechazar una empresa pendiente",
            description = "Se avisa a quien la registró, con el motivo si se da, y se borra entera: nadie de ella "
                    + "llegó a entrar. Queda en el historial con su nombre.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rechazada y borrada"),
            @ApiResponse(responseCode = "403", description = "Sin 'plataforma:gestionar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "No está pendiente, o tiene datos y no se puede borrar",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/empresas/{id}/rechazar")
    @PreAuthorize("hasAuthority('plataforma:gestionar')")
    public ResponseEntity<Void> rechazar(@PathVariable long id,
            @Valid @RequestBody(required = false) PlatformActionRequest peticion,
            @AuthenticationPrincipal SecurityUser usuario) {
        cicloDeLaEmpresa.rechazar(id, usuario.getUser(), peticion == null ? null : peticion.motivo());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Suspender una empresa activa",
            description = "Nadie de ella puede entrar ni fichar, tampoco en sus kioscos, y se cierran todas sus "
                    + "sesiones. Sus datos se quedan. Se avisa a sus ADMIN, con el motivo si se da.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Suspendida"),
            @ApiResponse(responseCode = "403", description = "Sin 'plataforma:gestionar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "No está activa, o es la del propio operador",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/empresas/{id}/suspender")
    @PreAuthorize("hasAuthority('plataforma:gestionar')")
    public ResponseEntity<Void> suspender(@PathVariable long id,
            @Valid @RequestBody(required = false) PlatformActionRequest peticion,
            @AuthenticationPrincipal SecurityUser usuario) {
        cicloDeLaEmpresa.suspender(id, usuario.getUser(), peticion == null ? null : peticion.motivo());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivar una empresa suspendida",
            description = "Su gente vuelve a poder entrar. Se avisa a sus ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reactivada"),
            @ApiResponse(responseCode = "403", description = "Sin 'plataforma:gestionar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "No está suspendida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/empresas/{id}/reactivar")
    @PreAuthorize("hasAuthority('plataforma:gestionar')")
    public ResponseEntity<Void> reactivar(@PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        cicloDeLaEmpresa.reactivar(id, usuario.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Comprobar la traza de auditoría entera",
            description = "Recorre la cadena de hashes de toda la instalación y dice, si está rota, en qué "
                    + "movimiento y de qué empresa: lo que a las empresas no se les dice cuando la rotura no es "
                    + "suya. Es el mismo recorrido que piden ellas, uno a la vez y compartido medio minuto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado",
                    content = @Content(schema = @Schema(implementation = PlatformIntegrityResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'plataforma:ver'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Hay otra comprobación en marcha y está tardando",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/integridad")
    @PreAuthorize("hasAuthority('plataforma:ver')")
    public ResponseEntity<PlatformIntegrityResponse> integridad() {
        // Directo al verificador y no a través del servicio, que es
        // transaccional: quien espera el turno no debe ocupar una conexión.
        return ResponseEntity.ok(verificador.verificarParaLaPlataforma());
    }
}
