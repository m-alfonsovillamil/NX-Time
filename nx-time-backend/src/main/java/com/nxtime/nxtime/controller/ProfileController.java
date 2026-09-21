package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.PersonalDataExport;
import com.nxtime.nxtime.dto.ProfileResponse;
import com.nxtime.nxtime.report.PersonalDataPdfGenerator;
import com.nxtime.nxtime.dto.UpdateProfileRequest;
import com.nxtime.nxtime.dto.VerifyPasswordRequest;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.EmployeeProfileService;
import com.nxtime.nxtime.service.PersonalDataExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * El perfil propio y el de los compañeros (Fase B).
 *
 * Separado de {@code /api/v1/gestor}: aquí viven las operaciones sobre
 * uno mismo, que no piden authority ninguna más allá de tener sesión.
 * Lo que un empleado NO puede cambiarse -- rol, jornada, vacaciones,
 * departamento -- no está en {@link UpdateProfileRequest} y se queda en
 * los endpoints de gestión, detrás de sus authorities.
 */
@RestController
@RequestMapping("/api/v1/perfil")
@Tag(name = "Perfil", description = "Datos personales del usuario autenticado, y consulta del perfil "
        + "de un compañero de la misma empresa.")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final EmployeeProfileService employeeProfileService;
    private final PersonalDataExportService exportService;
    private final PersonalDataPdfGenerator exportPdfGenerator;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(
            EmployeeProfileService employeeProfileService,
            PersonalDataExportService exportService,
            PersonalDataPdfGenerator exportPdfGenerator,
            PasswordEncoder passwordEncoder) {
        this.employeeProfileService = employeeProfileService;
        this.exportService = exportService;
        this.exportPdfGenerator = exportPdfGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "Mi perfil",
            description = "Datos personales y laborales, con el nombre completo y las iniciales del "
                    + "avatar ya calculados para que no los arme cada cliente a su manera. Incluye "
                    + "'authorities': lo que esta persona puede hacer, resuelto por el servidor, para "
                    + "que ningún cliente tenga que copiarse el reparto de permisos a su lenguaje. No "
                    + "autoriza nada -- eso lo sigue haciendo el @PreAuthorize de cada endpoint --, "
                    + "solo decide qué menú se le enseña a quien mira.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileResponse> getMyProfile(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(employeeProfileService.getMyProfile(usuario.getUser()));
    }

    @Operation(summary = "Cambiar mis datos personales",
            description = "Nombre, apellidos, fecha de nacimiento y puesto. Es un PATCH: lo que va a "
                    + "null no se toca, y una cadena vacía borra el dato. NO incluye rol, jornada, "
                    + "vacaciones ni departamento -- eso no lo decide uno sobre sí mismo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil actualizado",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Fecha de nacimiento futura, nombre vacío "
                    + "o algún campo demasiado largo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(employeeProfileService.updateMyProfile(request, usuario.getUser()));
    }

    @Operation(summary = "Comprobar mi propia contraseña",
            description = "Responde 204 si es correcta y 401 si no, y no hace nada más: no emite "
                    + "tokens, no abre sesión y no consume el límite de intentos del login. Lo usa "
                    + "la app para activar la huella, que antes hacía un /auth/login completo -- y "
                    + "eso dejaba tokens nuevos guardados y el refresh anterior vivo en el servidor.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "La contraseña es correcta"),
            @ApiResponse(responseCode = "400", description = "Contraseña vacía",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado, o la contraseña no es correcta",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/verificar-contrasena")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> verificarContrasena(
            @Valid @RequestBody VerifyPasswordRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        // La comprobación es contra el hash de ESTA persona, la que trae el
        // token: no se acepta un email, así que esto no sirve para probar
        // contraseñas de nadie más.
        if (!passwordEncoder.matches(request.contrasena(), usuario.getUser().getContrasena())) {
            throw new BadCredentialsException("Credenciales incorrectas.");
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "El perfil de un compañero",
            description = "Solo de la misma empresa (aislamiento multi-tenant a mano, ADR 006).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Sin la authority 'empleado:leer', "
                    + "o usuario de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{usuarioId}")
    @PreAuthorize("hasAuthority('empleado:leer')")
    public ResponseEntity<ProfileResponse> getProfile(
            @PathVariable long usuarioId, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(employeeProfileService.getProfile(usuarioId, usuario.getUser()));
    }

    @Operation(summary = "Descargar todos mis datos (JSON)",
            description = "Derecho de acceso y portabilidad (RGPD, arts. 15 y 20): perfil, todos los fichajes, "
                    + "pausas añadidas, ausencias, vacaciones, correcciones pedidas, horas extra, proyectos, "
                    + "avisos, adjuntos (sin su contenido), candidaturas y denuncias presentadas identificándose. "
                    + "Sin paginar. Solo los datos propios, y sin necesitar la aprobación de nadie. "
                    + "Lo que NO incluye va explicado dentro del propio fichero, en 'notas'.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichero JSON",
                    content = @Content(schema = @Schema(implementation = PersonalDataExport.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/mis-datos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PersonalDataExport> exportarMisDatos(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreDeFichero("json") + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(exportService.exportar(usuario.getUser()));
    }

    @Operation(summary = "Descargar todos mis datos (PDF)",
            description = "Lo mismo que el JSON, para leerlo sin herramientas. Sale del mismo objeto, así que "
                    + "no puede faltar en uno algo que esté en el otro. Las horas van en hora de España.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Documento PDF",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/mis-datos/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StreamingResponseBody> exportarMisDatosEnPdf(@AuthenticationPrincipal SecurityUser usuario) {
        // Los datos se leen AQUÍ, en el hilo de la petición y dentro de la
        // transacción del servicio; el PDF solo escribe lo que ya está leído.
        PersonalDataExport datos = exportService.exportar(usuario.getUser());
        StreamingResponseBody cuerpo = salida -> exportPdfGenerator.generar(datos, salida);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nombreDeFichero("pdf") + "\"")
                .body(cuerpo);
    }

    private static String nombreDeFichero(String extension) {
        return "nxtime-mis-datos-" + LocalDate.now(ZoneId.of("Europe/Madrid")) + "." + extension;
    }
}
