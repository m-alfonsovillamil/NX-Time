package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.JobApplicationRequest;
import com.nxtime.nxtime.dto.JobApplicationResponse;
import com.nxtime.nxtime.dto.JobPostingRequest;
import com.nxtime.nxtime.dto.JobPostingResponse;
import com.nxtime.nxtime.dto.UpdateApplicationStatusRequest;
import com.nxtime.nxtime.dto.UpdateJobPostingStatusRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.JobPostingService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ofertas internas y candidaturas (Fase H).
 *
 * <b>Leer y optar lo puede toda la plantilla</b> ({@code oferta:leer},
 * {@code candidatura:crear}): un tablón de vacantes internas al que no
 * llega la gente no es un tablón. Publicar y valorar empiezan en GESTOR.
 *
 * El CV <b>no viaja en ninguna petición</b>: lo adjunta el servidor, y
 * es el vigente de quien se presenta. Dejarlo elegir obligaría a
 * comprobar en cada llamada que el adjunto es tuyo, y permitiría
 * presentar una versión que ya se retiró.
 *
 * Para descargar el CV de una candidatura se usa el endpoint de
 * adjuntos que ya existe desde la fase B2 ({@code GET
 * /api/v1/adjuntos/{id}}), con el {@code cvAdjuntoId} que trae la
 * candidatura: descargar no pide authority, basta con ser de la misma
 * empresa, porque un gestor necesita leer el CV de su equipo.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Ofertas internas",
        description = "Vacantes internas y candidaturas de la plantilla.")
@SecurityRequirement(name = "bearerAuth")
public class JobPostingController {

    private final JobPostingService jobPostingService;

    public JobPostingController(JobPostingService jobPostingService) {
        this.jobPostingService = jobPostingService;
    }

    // ------------------------------------------------------------------
    // Ver y optar: toda la plantilla
    // ------------------------------------------------------------------

    @Operation(summary = "Las vacantes internas publicadas",
            description = "Las ABIERTAS de mi empresa. Las que ya pasaron su fecha de cierre "
                    + "siguen saliendo, marcadas con 'plazoVencido': esconderlas dejaría a quien "
                    + "la vio ayer sin saber qué pasó, y a quien se presentó sin su sitio.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Listado",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobPostingResponse.class)))))
    @GetMapping("/ofertas")
    @PreAuthorize("hasAuthority('oferta:leer')")
    public ResponseEntity<List<JobPostingResponse>> publicadas(
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(jobPostingService.publicadas(usuario.getUser()));
    }

    @Operation(summary = "Una oferta concreta",
            description = "Un BORRADOR solo lo ve quien puede publicar; para el resto no existe.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La oferta",
                    content = @Content(schema = @Schema(implementation = JobPostingResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe, es de otra empresa, o es un borrador ajeno",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/ofertas/{id}")
    @PreAuthorize("hasAuthority('oferta:leer')")
    public ResponseEntity<JobPostingResponse> detalle(
            @PathVariable long id,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(jobPostingService.detalle(id, usuario.getUser()));
    }

    @Operation(summary = "Presentar mi candidatura",
            description = "Adjunta el CV VIGENTE de quien se presenta, en este instante, y lo "
                    + "congela: a partir de aquí ese CV ya no se puede destruir, aunque su dueño "
                    + "suba otro. Sin CV en el perfil devuelve 400 — la app lo dice antes de "
                    + "dejar pulsar el botón.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Presentada",
                    content = @Content(schema = @Schema(implementation = JobApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "No tienes CV subido",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "La oferta no existe o es de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "No está publicada, el plazo venció, o ya te presentaste",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/ofertas/{id}/candidaturas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('candidatura:crear')")
    public JobApplicationResponse presentarCandidatura(
            @PathVariable long id,
            @Valid @RequestBody JobApplicationRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return jobPostingService.presentarCandidatura(id, request, usuario.getUser());
    }

    @Operation(summary = "Mis candidaturas",
            description = "En qué han quedado las vacantes a las que me he presentado.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Listado",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobApplicationResponse.class)))))
    @GetMapping("/candidaturas/mias")
    @PreAuthorize("hasAuthority('candidatura:crear')")
    public ResponseEntity<List<JobApplicationResponse>> misCandidaturas(
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(jobPostingService.misCandidaturas(usuario.getUser()));
    }

    // ------------------------------------------------------------------
    // Publicar y valorar: desde GESTOR
    // ------------------------------------------------------------------

    @Operation(summary = "Todas las ofertas de la empresa",
            description = "Borradores y cerradas incluidos: la vista de quien publica.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobPostingResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Sin permiso para publicar ofertas",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/ofertas/gestion")
    @PreAuthorize("hasAuthority('oferta:publicar')")
    public ResponseEntity<List<JobPostingResponse>> todas(
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(jobPostingService.todas(usuario.getUser()));
    }

    @Operation(summary = "Crear una oferta",
            description = "Nace en BORRADOR siempre: publicarla avisa a toda la plantilla, así "
                    + "que no puede ser el efecto colateral de guardar un formulario.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Creada",
                    content = @Content(schema = @Schema(implementation = JobPostingResponse.class))),
            @ApiResponse(responseCode = "400", description = "Faltan título o descripción, o la fecha de cierre ya pasó",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin permiso para publicar ofertas",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/ofertas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('oferta:publicar')")
    public JobPostingResponse crear(
            @Valid @RequestBody JobPostingRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return jobPostingService.crear(request, usuario.getUser());
    }

    @Operation(summary = "Editar una oferta",
            description = "Solo el contenido. El estado se cambia en su propio endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Editada",
                    content = @Content(schema = @Schema(implementation = JobPostingResponse.class))),
            @ApiResponse(responseCode = "403", description = "Es de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/ofertas/{id}")
    @PreAuthorize("hasAuthority('oferta:publicar')")
    public ResponseEntity<JobPostingResponse> editar(
            @PathVariable long id,
            @Valid @RequestBody JobPostingRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(jobPostingService.editar(id, request, usuario.getUser()));
    }

    @Operation(summary = "Publicar, retirar o cerrar una oferta",
            description = "El aviso a la plantilla sale UNA vez, la primera que se publica: "
                    + "retirarla al borrador y volver a publicarla no vuelve a avisar a nadie. "
                    + "Una oferta cerrada no se reabre.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado",
                    content = @Content(schema = @Schema(implementation = JobPostingResponse.class))),
            @ApiResponse(responseCode = "403", description = "Es de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya estaba en ese estado, o estaba cerrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/ofertas/{id}/estado")
    @PreAuthorize("hasAuthority('oferta:publicar')")
    public ResponseEntity<JobPostingResponse> cambiarEstado(
            @PathVariable long id,
            @Valid @RequestBody UpdateJobPostingStatusRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(
                jobPostingService.cambiarEstado(id, request, usuario.getUser()));
    }

    @Operation(summary = "Las candidaturas de una oferta",
            description = "Cada una trae el 'cvAdjuntoId' que se congeló al presentarse: es lo "
                    + "que hay que descargar por /api/v1/adjuntos/{id} para leer el CV tal como "
                    + "estaba, no el que la persona tenga hoy.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobApplicationResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Sin permiso, o es de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/ofertas/{id}/candidaturas")
    @PreAuthorize("hasAuthority('candidatura:gestionar')")
    public ResponseEntity<List<JobApplicationResponse>> candidaturasDeOferta(
            @PathVariable long id,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(
                jobPostingService.candidaturasDeOferta(id, usuario.getUser()));
    }

    @Operation(summary = "Valorar una candidatura",
            description = "Descartarla exige comentario: lo lee un compañero, sobre sí mismo, en "
                    + "la empresa en la que sigue trabajando mañana. Y nadie valora la suya "
                    + "propia, tenga el rol que tenga.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Valorada",
                    content = @Content(schema = @Schema(implementation = JobApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Se descarta sin comentario",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Es tu propia candidatura, o es de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya estaba resuelta, o ya estaba en ese estado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/candidaturas/{id}/estado")
    @PreAuthorize("hasAuthority('candidatura:gestionar')")
    public ResponseEntity<JobApplicationResponse> valorar(
            @PathVariable long id,
            @Valid @RequestBody UpdateApplicationStatusRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(jobPostingService.valorar(id, request, usuario.getUser()));
    }
}
