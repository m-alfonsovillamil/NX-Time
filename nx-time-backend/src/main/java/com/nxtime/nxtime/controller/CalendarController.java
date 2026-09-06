package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.CalendarResponse;
import com.nxtime.nxtime.dto.HolidayRequest;
import com.nxtime.nxtime.dto.HolidayResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calendario laboral: festivos y ausencias, mes a mes (Fase C).
 *
 * Los festivos que se pueden dar de alta aquí son los de la empresa
 * (autonómicos, locales o de convenio). Los nacionales los calcula y
 * siembra el sistema la primera vez que alguien mira ese año, y no se
 * editan por API: son una única fila compartida por todas las empresas,
 * así que borrar Navidad desde una se la quitaría a todas las demás.
 */
@RestController
@RequestMapping("/api/v1/calendario")
@Tag(name = "Calendario", description = "Festivos del calendario laboral y ausencias del mes.")
@SecurityRequirement(name = "bearerAuth")
public class CalendarController {

    /** "El mes actual" es el de España, no el de UTC (ver TimeEntryMapper). */
    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Operation(summary = "El calendario de un mes",
            description = "Festivos aplicables a la empresa y ausencias que tocan ese mes. Sin "
                    + "parámetros devuelve el mes en curso. Los festivos nacionales del año se "
                    + "siembran en la primera consulta, así que un año que nadie ha mirado nunca "
                    + "ya llega completo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El mes pedido",
                    content = @Content(schema = @Schema(implementation = CalendarResponse.class))),
            @ApiResponse(responseCode = "400", description = "Mes fuera de 1..12 o año fuera de 2000..2100",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'calendario:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('calendario:leer')")
    public ResponseEntity<CalendarResponse> verMes(
            @Parameter(description = "Año a consultar. Por defecto, el actual.")
            @RequestParam(required = false) Integer anio,
            @Parameter(description = "Mes (1-12). Por defecto, el actual.")
            @RequestParam(required = false) Integer mes,
            @Parameter(description = "Incluir las ausencias del equipo. Se atiende solo con la "
                    + "authority 'ausencia:leer:equipo'; sin ella la respuesta llega con las "
                    + "propias e 'incluyeEquipo' a false, en vez de un 403.")
            @RequestParam(required = false, defaultValue = "false") boolean equipo,
            @AuthenticationPrincipal SecurityUser usuario) {

        YearMonth ahora = YearMonth.now(MADRID_ZONE);
        return ResponseEntity.ok(calendarService.verMes(
                anio != null ? anio : ahora.getYear(),
                mes != null ? mes : ahora.getMonthValue(),
                equipo,
                usuario.getUser()));
    }

    @Operation(summary = "Añadir un festivo de la empresa",
            description = "Autonómico, local o de convenio. El ámbito NACIONAL se rechaza: esos "
                    + "los pone el sistema.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Creado",
                    content = @Content(schema = @Schema(implementation = HolidayResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o ámbito NACIONAL",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'calendario:gestionar'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Esa fecha ya es festivo de la empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/festivos")
    @PreAuthorize("hasAuthority('calendario:gestionar')")
    public ResponseEntity<HolidayResponse> crearFestivo(
            @Valid @RequestBody HolidayRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(calendarService.crear(request, usuario.getUser()));
    }

    @Operation(summary = "Cambiar un festivo de la empresa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Actualizado",
                    content = @Content(schema = @Schema(implementation = HolidayResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o ámbito NACIONAL",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin authority, festivo nacional, o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Festivo no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Esa fecha ya es festivo de la empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/festivos/{id}")
    @PreAuthorize("hasAuthority('calendario:gestionar')")
    public ResponseEntity<HolidayResponse> editarFestivo(
            @PathVariable long id,
            @Valid @RequestBody HolidayRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(calendarService.editar(id, request, usuario.getUser()));
    }

    @Operation(summary = "Quitar un festivo de la empresa")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Borrado"),
            @ApiResponse(responseCode = "403", description = "Sin authority, festivo nacional, o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Festivo no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/festivos/{id}")
    @PreAuthorize("hasAuthority('calendario:gestionar')")
    public ResponseEntity<Void> borrarFestivo(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        calendarService.borrar(id, usuario.getUser());
        return ResponseEntity.noContent().build();
    }
}
