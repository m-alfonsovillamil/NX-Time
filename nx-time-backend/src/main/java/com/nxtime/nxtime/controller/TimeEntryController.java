package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.domain.CorrectionStatus;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.dto.AddPauseRequest;
import com.nxtime.nxtime.dto.AddPauseResponse;
import com.nxtime.nxtime.dto.AddedPauseDTO;
import com.nxtime.nxtime.dto.ChangeProjectRequest;
import com.nxtime.nxtime.dto.ClockProjectsResponse;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.dto.TimeEntryResponse;
import com.nxtime.nxtime.dto.TodayStatusResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.mapper.TimeEntryMapper;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.AddedPauseService;
import com.nxtime.nxtime.service.CorrectionService;
import com.nxtime.nxtime.service.TimeEntryService;
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
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para gestionar el registro horario.
 *
 * Desde esta fase ya NO devuelve la entidad TimeEntry directamente
 * (ver auditoría, defecto #1: la entidad arrastraba al Usuario, y con
 * él, su contraseña cifrada). Cada endpoint mapea a un DTO explícito.
 */
@RestController
@RequestMapping("/api/v1/fichaje")
@Tag(name = "Fichaje", description = "Registro horario: iniciar/finalizar jornada, pausas e historial.")
@SecurityRequirement(name = "bearerAuth")
public class TimeEntryController {

    private final TimeEntryService timeEntryService;
    private final TimeEntryMapper timeEntryMapper;
    private final CorrectionService correctionService;
    private final AddedPauseService addedPauseService;

    public TimeEntryController(
            TimeEntryService timeEntryService,
            TimeEntryMapper timeEntryMapper,
            CorrectionService correctionService,
            AddedPauseService addedPauseService) {
        this.timeEntryService = timeEntryService;
        this.timeEntryMapper = timeEntryMapper;
        this.correctionService = correctionService;
        this.addedPauseService = addedPauseService;
    }

    @Operation(summary = "Fichar (INICIO/FIN/PAUSA_INICIO/PAUSA_FIN)",
            description = "Avanza la máquina de estados del fichaje del usuario autenticado. En INICIO se puede "
                    + "mandar 'proyectoId' (ver GET /fichaje/proyectos); sin él, con un solo proyecto asignado se usa "
                    + "ese, y con varios la jornada empieza sin proyecto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichaje registrado",
                    content = @Content(schema = @Schema(implementation = TimeEntryResponse.class))),
            @ApiResponse(responseCode = "400", description = "'tipo' ausente o inválido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:escribir'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Transición inválida "
                    + "(ej. iniciar con una jornada ya activa, pausar sin jornada...)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:escribir')")
    @PostMapping
    public ResponseEntity<TimeEntryResponse> registerTimeEntry(
            @Valid @RequestBody TimeEntryRequest request, Authentication authentication) {
        var entry = timeEntryService.registerTimeEntry(authentication.getName(), request);
        return ResponseEntity.ok(timeEntryMapper.toResponse(entry));
    }

    @Operation(summary = "Consultar la jornada activa", description = "204 si no hay ninguna jornada abierta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jornada activa",
                    content = @Content(schema = @Schema(implementation = TimeEntryResponse.class))),
            @ApiResponse(responseCode = "204", description = "No hay jornada activa"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer')")
    @GetMapping("/activo")
    public ResponseEntity<TimeEntryResponse> getActiveTimeEntry(Authentication authentication) {
        return timeEntryService.getActiveTimeEntry(authentication.getName())
                .map(timeEntryMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Mis proyectos para fichar",
            description = "Los proyectos en los que puedo fichar hoy (asignación vigente y proyecto activo) y el de "
                    + "la jornada en curso, si la hay. Con dos o más disponibles, la app pregunta al iniciar la "
                    + "jornada y manda 'proyectoId' en el INICIO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proyectos",
                    content = @Content(schema = @Schema(implementation = ClockProjectsResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer')")
    @GetMapping("/proyectos")
    public ResponseEntity<ClockProjectsResponse> getProyectos(Authentication authentication) {
        return ResponseEntity.ok(timeEntryService.proyectosParaFichar(authentication.getName()));
    }

    @Operation(summary = "Cambiar de proyecto durante la jornada",
            description = "Cierra el tramo del proyecto en curso y abre otro desde ahora. Solo en la jornada propia, "
                    + "abierta y fuera de pausa. Queda en la traza del fichaje.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proyecto cambiado",
                    content = @Content(schema = @Schema(implementation = ClockProjectsResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Fichaje de otra persona o proyecto no asignado hoy",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Fichaje no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Jornada cerrada, en pausa, o ya en ese proyecto",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:escribir')")
    @PostMapping("/{id}/proyecto")
    public ResponseEntity<ClockProjectsResponse> cambiarProyecto(
            @PathVariable long id,
            @Valid @RequestBody ChangeProjectRequest peticion,
            Authentication authentication) {
        return ResponseEntity.ok(timeEntryService.cambiarProyecto(authentication.getName(), id, peticion.proyectoId()));
    }

    @Operation(summary = "¿Es hoy laborable para mí?",
            description = "Lo pregunta la app antes de iniciar la jornada. No laborable = festivo de la empresa o "
                    + "ausencia aprobada (los fines de semana no cuentan). Iniciar la jornada igualmente está "
                    + "permitido, y avisa a quien aprueba ausencias.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado del día",
                    content = @Content(schema = @Schema(implementation = TodayStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer')")
    @GetMapping("/hoy")
    public ResponseEntity<TodayStatusResponse> getHoy(Authentication authentication) {
        return ResponseEntity.ok(timeEntryService.motivoNoLaborableHoy(authentication.getName())
                .map(motivo -> new TodayStatusResponse(false, motivo.texto()))
                .orElse(new TodayStatusResponse(true, null)));
    }

    @Operation(summary = "Historial de fichajes propio",
            description = "Sin fechas, los últimos 200, más recientes primero. Con 'desde' y 'hasta' (días de España, "
                    + "los dos incluidos, formato YYYY-MM-DD): todos los de ese periodo, sin límite de filas y con "
                    + "un año como máximo. Hay que pasar las dos o ninguna.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TimeEntryResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Solo una de las dos fechas, fecha mal escrita, "
                    + "inicio posterior al fin, o más de un año",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer')")
    @GetMapping("/historial")
    public ResponseEntity<List<TimeEntryResponse>> getHistory(
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            Authentication authentication) {
        if ((desde == null) != (hasta == null)) {
            throw new BusinessException("Para filtrar el historial hacen falta las dos fechas.", HttpStatus.BAD_REQUEST);
        }
        List<TimeEntry> fichajes = desde == null
                ? timeEntryService.getHistory(authentication.getName())
                : timeEntryService.getHistory(authentication.getName(), desde, hasta);
        List<TimeEntryResponse> history = fichajes.stream().map(timeEntryMapper::toResponse).toList();
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Historial de fichajes del equipo (gestor)",
            description = "Solo los EMPLEADO de la empresa del gestor autenticado, nunca otros gestores.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial del equipo",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = TeamTimeEntryDTO.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'fichaje:leer:equipo'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer:equipo')")
    @GetMapping("/gestor/historial")
    public ResponseEntity<List<TeamTimeEntryDTO>> getTeamHistory(Authentication authentication) {
        return ResponseEntity.ok(timeEntryService.getTeamHistory(authentication.getName()));
    }

    @Operation(summary = "Pedir que se corrija un fichaje pasado",
            description = """
                    Sustituye al PATCH /api/v1/fichaje/{id} de antes, que corregía EN EL ACTO.

                    Ahora **crea una solicitud** y el fichaje no se toca hasta que alguien la                     aprueba. Quién aprueba depende de quién pide: si la pides sobre tu propio                     fichaje la aprueba alguien con 'correccion:aprobar'; si la pides sobre el                     fichaje de otra persona (necesitas 'fichaje:corregir') la aprueba ELLA, que                     también puede disputarla.

                    Se aplica en el acto en un solo caso: que sea tu fichaje y tengas                     'correccion:aprobar'. Sin esa excepción, un ADMIN no podría corregir nunca                     su propio fichaje. La auditoría lo deja anotado como auto-aprobación.

                    Devuelve 202 si queda pendiente y 200 si se ha aplicado: el estado de la                     respuesta dice cuál de los dos ha pasado.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aplicada en el acto (auto-aprobación)",
                    content = @Content(schema = @Schema(implementation = CorrectionResponse.class))),
            @ApiResponse(responseCode = "202", description = "Solicitud creada, pendiente de aprobación",
                    content = @Content(schema = @Schema(implementation = CorrectionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o horaSalida no posterior a horaEntrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Fichaje de otro sin 'fichaje:corregir', o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Fichaje no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Jornada sin cerrar, ya corregida, o con otra solicitud viva",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('correccion:solicitar')")
    @PostMapping("/{id}/correcciones")
    public ResponseEntity<CorrectionResponse> solicitarCorreccion(
            @PathVariable long id,
            @Valid @RequestBody CorrectionRequestDTO request,
            @AuthenticationPrincipal SecurityUser usuario) {
        CorrectionResponse solicitud = correctionService.solicitar(id, request, usuario.getUser());
        // 202 = "aceptado, pero todavía no ha pasado nada con el fichaje".
        // Es la diferencia que importa para quien llama: con 200 el
        // fichaje YA cambió.
        HttpStatus estado = solicitud.estado() == CorrectionStatus.APROBADA
                ? HttpStatus.OK
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(estado).body(solicitud);
    }

    @Operation(summary = "Añadir una pausa que no se fichó en su momento",
            description = """
                    Para "se me olvidó darle a pausar para comer" (ADR 015). Siempre con motivo.

                    **El servidor decide qué pasa**, y la respuesta lo dice:
                    - Jornada abierta, o cerrada que empezó hoy: se aplica en el acto (201).
                    - Día pasado: se pide como corrección y la aprueba quien corresponda (202).
                      Si quien la pide puede aprobarse a sí mismo, se aplica y responde 201.

                    Solo sobre tus propios fichajes: para el de otra persona, pide una corrección.
                    No hay tope de duración, pero la pausa tiene que caber en la jornada y no
                    solaparse con otras.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Aplicada: el tiempo trabajado ya ha cambiado",
                    content = @Content(schema = @Schema(implementation = AddPauseResponse.class))),
            @ApiResponse(responseCode = "202", description = "Pedida como corrección, pendiente de aprobación",
                    content = @Content(schema = @Schema(implementation = AddPauseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o la pausa acaba antes de empezar",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Fichaje de otra persona o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Fichaje no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "No cabe en la jornada, se solapa, dejaría más pausa "
                    + "que jornada, o el fichaje tiene una corrección sin resolver",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:escribir')")
    @PostMapping("/{id}/pausas")
    public ResponseEntity<AddPauseResponse> anadirPausa(
            @PathVariable long id,
            @Valid @RequestBody AddPauseRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        AddedPauseService.Resultado resultado = addedPauseService.anadir(id, request, usuario.getUser());
        AddPauseResponse cuerpo = new AddPauseResponse(
                resultado.aplicada(),
                resultado.fichaje() != null ? timeEntryMapper.toResponse(resultado.fichaje()) : null,
                resultado.correccion());
        return ResponseEntity.status(resultado.aplicada() ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(cuerpo);
    }

    @Operation(summary = "Pausas añadidas a posteriori en una jornada",
            description = "Las que siguen en pie. Las fichadas con el botón no salen: no tienen intervalo guardado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pausas añadidas",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AddedPauseDTO.class)))),
            @ApiResponse(responseCode = "403", description = "Fichaje de otra persona sin 'fichaje:leer:equipo'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Fichaje no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:leer')")
    @GetMapping("/{id}/pausas")
    public ResponseEntity<List<AddedPauseDTO>> pausasAnadidas(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(addedPauseService.deLaJornada(id, usuario.getUser()));
    }

    @Operation(summary = "Deshacer una pausa añadida",
            description = """
                    Solo sobre la jornada abierta y solo sobre tus fichajes. Una vez cerrada, quitar
                    una pausa sube el tiempo trabajado y ya no es autoservicio: se pide una corrección.
                    La pausa no se borra, se marca anulada.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deshecha: el fichaje actualizado",
                    content = @Content(schema = @Schema(implementation = TimeEntryResponse.class))),
            @ApiResponse(responseCode = "403", description = "Fichaje de otra persona o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Fichaje o pausa no encontrados",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Jornada ya cerrada, o pausa ya deshecha",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PreAuthorize("hasAuthority('fichaje:escribir')")
    @DeleteMapping("/{id}/pausas/{pausaId}")
    public ResponseEntity<TimeEntryResponse> deshacerPausa(
            @PathVariable long id, @PathVariable long pausaId,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(timeEntryMapper.toResponse(
                addedPauseService.anular(id, pausaId, usuario.getUser())));
    }
}
