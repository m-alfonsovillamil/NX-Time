package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.CambioDeRolRequest;
import com.nxtime.nxtime.dto.EstadoInvitacionesResponse;
import com.nxtime.nxtime.dto.ImportacionPlantillaResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.AuthService;
import com.nxtime.nxtime.service.ImportacionDePlantillaService;
import com.nxtime.nxtime.service.InvitacionesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * La plantilla de golpe (ADR 052): importarla desde un CSV o un Excel, mandar
 * las invitaciones que esperan cupo y cambiar el rol de alguien.
 */
@RestController
@Tag(name = "Plantilla", description = "Importar la plantilla desde un fichero, las invitaciones de alta pendientes "
        + "y el rol de cada persona.")
@SecurityRequirement(name = "bearerAuth")
public class PlantillaController {

    private final ImportacionDePlantillaService importacion;
    private final InvitacionesService invitaciones;
    private final AuthService authService;

    public PlantillaController(ImportacionDePlantillaService importacion, InvitacionesService invitaciones,
            AuthService authService) {
        this.importacion = importacion;
        this.invitaciones = invitaciones;
        this.authService = authService;
    }

    @Operation(summary = "Importar la plantilla desde un CSV o un Excel",
            description = "Una persona por fila, con la fila de títulos delante: nombre, apellidos y email, y si se "
                    + "quiere rol, departamento, horas y vacaciones. CSV en UTF-8 o Windows-1252, separado por punto y "
                    + "coma, coma o tabulador; o .xlsx (la primera hoja). Como mucho 500 personas. Con simular=true "
                    + "(por defecto) solo comprueba y devuelve cada fila con sus errores. Con simular=false crea a "
                    + "todas, o a ninguna si alguna fila tiene errores, y empieza a mandar las invitaciones de alta "
                    + "que quepan hoy (50 al día por empresa): las demás quedan pendientes. Cada fila pide los "
                    + "permisos de quien importa: roles por encima de EMPLEADO, gestor:crear; departamento, "
                    + "departamento:gestionar; horas y vacaciones, empleado:configurar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comprobado (y, si no era una simulación, importado)",
                    content = @Content(schema = @Schema(implementation = ImportacionPlantillaResponse.class))),
            @ApiResponse(responseCode = "400", description = "El fichero no se puede leer, le faltan columnas, pasa "
                    + "de 500 personas, o (al importar) alguna fila tiene errores",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empleado:crear'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/api/v1/gestor/plantilla/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('empleado:crear')")
    public ResponseEntity<ImportacionPlantillaResponse> importar(
            @RequestPart("fichero") MultipartFile fichero,
            @RequestParam(defaultValue = "true") boolean simular,
            @AuthenticationPrincipal SecurityUser actor) {
        byte[] contenido;
        try {
            contenido = fichero.getBytes();
        } catch (IOException e) {
            throw new BusinessException("No se ha podido leer el fichero.", HttpStatus.BAD_REQUEST);
        }
        if (simular) {
            return ResponseEntity.ok(importacion.simular(fichero.getOriginalFilename(), contenido, actor.getUser()));
        }
        // La importación se confirma antes de mandar nada: las invitaciones
        // necesitan que las cuentas ya existan para todos.
        ImportacionPlantillaResponse hecha = importacion.importar(fichero.getOriginalFilename(), contenido,
                actor.getUser());
        EstadoInvitacionesResponse estado = invitaciones.enviarPendientes(actor.getUser());
        return ResponseEntity.ok(new ImportacionPlantillaResponse(false, hecha.filas(), hecha.conErrores(),
                hecha.creadas(), hecha.columnasIgnoradas(), hecha.detalle(), estado));
    }

    @Operation(summary = "Las invitaciones de alta pendientes",
            description = "Cuántas cuentas esperan su correo de alta y cuántas pueden salir todavía hoy.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado",
                    content = @Content(schema = @Schema(implementation = EstadoInvitacionesResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empleado:crear'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/api/v1/gestor/plantilla/invitaciones")
    @PreAuthorize("hasAuthority('empleado:crear')")
    public ResponseEntity<EstadoInvitacionesResponse> invitaciones(@AuthenticationPrincipal SecurityUser actor) {
        return ResponseEntity.ok(invitaciones.estado(actor.getUser()));
    }

    @Operation(summary = "Mandar las invitaciones pendientes",
            description = "Empieza a mandar, en segundo plano, las que quepan hoy. Si ya se están mandando, no hace "
                    + "nada más. Llamarla otra vez al día siguiente manda las siguientes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Las que se están mandando, y las que quedan",
                    content = @Content(schema = @Schema(implementation = EstadoInvitacionesResponse.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empleado:crear'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/api/v1/gestor/plantilla/invitaciones/enviar")
    @PreAuthorize("hasAuthority('empleado:crear')")
    public ResponseEntity<EstadoInvitacionesResponse> enviarInvitaciones(@AuthenticationPrincipal SecurityUser actor) {
        return ResponseEntity.ok(invitaciones.enviarPendientes(actor.getUser()));
    }

    @Operation(summary = "Cambiar el rol de alguien de la empresa",
            description = "EMPLEADO, GESTOR, RRHH o ADMIN. Solo un ADMIN, y nunca el suyo: así siempre queda al "
                    + "menos un ADMIN. Vale desde la siguiente petición de esa persona.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cambiado (o ya lo tenía)"),
            @ApiResponse(responseCode = "400", description = "Falta el rol o no existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin 'gestor:crear', de otra empresa o el propio",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/api/v1/gestor/empleados/{id}/rol")
    @PreAuthorize("hasAuthority('gestor:crear')")
    public ResponseEntity<Void> cambiarRol(@PathVariable long id, @Valid @RequestBody CambioDeRolRequest request,
            @AuthenticationPrincipal SecurityUser actor) {
        authService.cambiarRol(id, request.rol(), actor.getUser());
        return ResponseEntity.noContent().build();
    }
}
