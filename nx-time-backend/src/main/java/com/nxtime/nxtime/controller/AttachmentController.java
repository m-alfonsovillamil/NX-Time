package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.domain.AttachmentType;
import com.nxtime.nxtime.dto.AttachmentResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * El CV y la foto de perfil (Fase B2). Ver ADR 007.
 *
 * Subir y borrar son cosas de uno mismo; <b>descargar es de empresa</b>,
 * porque un gestor necesita poder leer el CV de su equipo. Esa asimetría
 * está en el servicio, que compara la persona al borrar y la empresa al
 * descargar.
 */
@RestController
@RequestMapping("/api/v1/perfil/adjuntos")
@Tag(name = "Adjuntos", description = "Currículum y foto de perfil. Un CV y una foto vigentes por "
        + "persona: subir otro reemplaza el anterior.")
@SecurityRequirement(name = "bearerAuth")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Operation(summary = "Subir mi CV o mi foto",
            description = "multipart/form-data. El tipo del fichero se comprueba por sus PRIMEROS "
                    + "BYTES, no por la extensión ni por el Content-Type declarado. Una FOTO se "
                    + "reescala en el servidor a 256x256 JPEG antes de guardarse, así que lo "
                    + "almacenado no es lo que se envió. Máximo 5 MB.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adjunto guardado",
                    content = @Content(schema = @Schema(implementation = AttachmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Fichero vacío, o su contenido no es del "
                    + "tipo esperado (un .exe renombrado a .pdf cae aquí)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'adjunto:subir'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('adjunto:subir')")
    public ResponseEntity<AttachmentResponse> subir(
            @Parameter(description = "El fichero") @RequestParam("fichero") MultipartFile fichero,
            @Parameter(description = "CV o FOTO") @RequestParam("tipo") AttachmentType tipo,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(attachmentService.subir(fichero, tipo, usuario.getUser()));
    }

    @Operation(summary = "Mis adjuntos", description = "Sin contenido: solo nombre, tamaño y fecha.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AttachmentResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AttachmentResponse>> listar(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(attachmentService.listar(usuario.getUser()));
    }

    @Operation(summary = "Descargar un adjunto",
            description = "De cualquiera de la misma empresa: un gestor necesita leer el CV de su "
                    + "equipo. Una FOTO va 'inline' para poder pintarse; un CV, 'attachment'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El contenido"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Adjunto de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Adjunto no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ByteArrayResource> descargar(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {

        AttachmentService.ContenidoDeAdjunto adjunto =
                attachmentService.descargar(id, usuario.getUser());

        // La foto se pinta dentro de la aplicación; el CV se descarga.
        String disposicion = adjunto.tipo() == AttachmentType.FOTO ? "inline" : "attachment";

        /*
         * Un recurso con Content-Length, y NO un StreamingResponseBody
         * como los informes.
         *
         * Allí tiene sentido: el Excel se genera al vuelo y no se sabe
         * cuánto ocupará. Aquí los bytes ya están enteros en memoria --
         * se acaban de leer de la base --, así que "streaming" solo
         * significaría mandarlos troceados y sin longitud, que es peor
         * por dos motivos: el cliente no puede enseñar progreso, y una
         * respuesta chunked ya dio guerra antes en este proyecto (el
         * interceptor de logging de OkHttp muere con EOFException sobre
         * ellas).
         */
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(adjunto.mime()))
                .contentLength(adjunto.contenido().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposicion + "; filename=\"" + adjunto.nombreOriginal() + "\"")
                .body(new ByteArrayResource(adjunto.contenido()));
    }

    @Operation(summary = "Borrar un adjunto mío",
            description = "Solo los propios: un gestor puede leer el CV de su equipo, pero no "
                    + "borrárselo a nadie.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Borrado"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "El adjunto es de otra persona",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Adjunto no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('adjunto:subir')")
    public ResponseEntity<Void> borrar(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        attachmentService.borrar(id, usuario.getUser());
        return ResponseEntity.noContent().build();
    }
}
