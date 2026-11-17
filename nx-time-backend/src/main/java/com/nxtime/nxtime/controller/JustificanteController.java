package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.ActivarJustificantesRequest;
import com.nxtime.nxtime.dto.JustificanteResponse;
import com.nxtime.nxtime.dto.JustificantesResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.JustificanteService;
import com.nxtime.nxtime.web.CabeceraDeDescarga;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * El justificante de una ausencia (ADR 062). Es un dato de salud: lo ven la
 * persona y RRHH/ADMIN, cada descarga de otra persona queda anotada, y la
 * empresa tiene que activarlo (apagado por defecto).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Justificantes", description = "El justificante de una ausencia: solo la persona y RRHH/ADMIN.")
@SecurityRequirement(name = "bearerAuth")
public class JustificanteController {

    private final JustificanteService justificantes;

    public JustificanteController(JustificanteService justificantes) {
        this.justificantes = justificantes;
    }

    @Operation(summary = "Los justificantes de unas ausencias",
            description = "Dice si la empresa los tiene activados y, de las ausencias pedidas (hasta 200), los "
                    + "justificantes que quien pregunta puede ver: los suyos, y con ausencia:justificante:leer "
                    + "los de su empresa. Sin los bytes.")
    @GetMapping("/ausencias/justificantes")
    @PreAuthorize("hasAuthority('ausencia:leer')")
    public ResponseEntity<JustificantesResponse> justificantesDeAusencias(
            @RequestParam(name = "peticiones", required = false) List<Long> peticiones,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(justificantes.de(peticiones, usuario.getUser()));
    }

    @Operation(summary = "Adjuntar el justificante de una ausencia mía",
            description = "multipart/form-data, campo «fichero». PDF, JPEG o PNG, comprobado por los primeros "
                    + "bytes. Máximo 5 MB. Reemplaza el que hubiera.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "La ausencia es de otra persona o de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "La empresa no tiene activados los justificantes",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/ausencias/{id}/justificante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ausencia:escribir')")
    public ResponseEntity<JustificanteResponse> subirJustificante(
            @PathVariable long id, @RequestParam("fichero") MultipartFile fichero,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(justificantes.subir(id, fichero, usuario.getUser()));
    }

    @Operation(summary = "Descargar el justificante de una ausencia",
            description = "La persona, o quien tenga ausencia:justificante:leer en su empresa (queda anotado).")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "Ni es tuya ni puedes ver justificantes de otros",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "La ausencia no existe o no tiene justificante",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/ausencias/{id}/justificante")
    @PreAuthorize("hasAuthority('ausencia:leer')")
    public ResponseEntity<ByteArrayResource> descargarJustificante(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        JustificanteService.Contenido c = justificantes.descargar(id, usuario.getUser());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.mime()))
                .contentLength(c.contenido().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, CabeceraDeDescarga.de("attachment", c.nombre()))
                // Un dato de salud no se queda en ninguna caché.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ByteArrayResource(c.contenido()));
    }

    @Operation(summary = "Quitar el justificante de una ausencia mía")
    @DeleteMapping("/ausencias/{id}/justificante")
    @PreAuthorize("hasAuthority('ausencia:escribir')")
    public ResponseEntity<Void> borrarJustificante(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        justificantes.borrar(id, usuario.getUser());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Activar los justificantes en las ausencias",
            description = "Apagado por defecto. Desactivarlo no borra los que haya: se siguen viendo.")
    @PutMapping("/empresa/justificantes")
    @PreAuthorize("hasAuthority('empresa:configurar')")
    public ResponseEntity<JustificantesResponse> activarJustificantes(
            @Valid @RequestBody ActivarJustificantesRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(justificantes.activar(request.activo(), usuario.getUser()));
    }
}
