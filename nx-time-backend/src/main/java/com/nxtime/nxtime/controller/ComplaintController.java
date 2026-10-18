package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.ComplaintChannelInfoResponse;
import com.nxtime.nxtime.dto.ComplaintCreatedResponse;
import com.nxtime.nxtime.dto.ComplaintExtensionRequest;
import com.nxtime.nxtime.dto.ComplaintMessageRequest;
import com.nxtime.nxtime.dto.ComplaintRedactionRequest;
import com.nxtime.nxtime.dto.ComplaintResponse;
import com.nxtime.nxtime.dto.ComplaintSummaryResponse;
import com.nxtime.nxtime.dto.ComplaintTrackingRequest;
import com.nxtime.nxtime.dto.CreateComplaintRequest;
import com.nxtime.nxtime.dto.DataAccessResponse;
import com.nxtime.nxtime.dto.UpdateComplaintStatusRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.ComplaintService;
import com.nxtime.nxtime.service.impl.CanalDeDenuncias;
import com.nxtime.nxtime.service.impl.RegistroDeAccesos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * El canal interno de información de la Ley 2/2023 (Fase G).
 *
 * Los endpoints están en <b>dos bloques que no se tocan</b>, y el corte
 * es el que manda:
 *
 * <ul>
 *   <li>{@code /seguimiento} — la puerta del denunciante, con el código en
 *       el cuerpo y nunca en la URL (ADR 034). Pide
 *       estar autenticado (el canal es interno) pero <b>no comprueba
 *       quién eres</b>: el código es la credencial, y comprobar la
 *       identidad sería negar el anonimato.</li>
 *   <li>{@code /{id}} — la puerta de quien instruye, con
 *       {@code denuncia:instruir}. Esa authority solo la tiene ADMIN, y
 *       no por jerarquía sino porque la Ley 2/2023 obliga a designar un
 *       Responsable del Sistema Interno de Información. <b>Un GESTOR no
 *       las ve</b>, y no es un olvido de reparto: la denuncia puede ser
 *       sobre él.</li>
 * </ul>
 *
 * No hay ningún endpoint que devuelva un código de seguimiento ya
 * emitido. No es que falte: no se puede escribir, porque de él solo se
 * guarda el hash.
 */
@RestController
@RequestMapping("/api/v1/denuncias")
@Tag(name = "Canal de denuncias",
        description = "Canal interno de información de la Ley 2/2023, con anonimato opcional.")
@SecurityRequirement(name = "bearerAuth")
public class ComplaintController {

    private final ComplaintService complaintService;
    private final CanalDeDenuncias canalDeDenuncias;
    private final RegistroDeAccesos registroDeAccesos;

    public ComplaintController(ComplaintService complaintService, CanalDeDenuncias canalDeDenuncias,
            RegistroDeAccesos registroDeAccesos) {
        this.complaintService = complaintService;
        this.canalDeDenuncias = canalDeDenuncias;
        this.registroDeAccesos = registroDeAccesos;
    }

    // ------------------------------------------------------------------
    // Denunciar y seguir
    // ------------------------------------------------------------------

    @Operation(summary = "Presentar una denuncia",
            description = "Devuelve el código de seguimiento UNA sola vez. No se guarda en "
                    + "claro y no hay forma de volver a pedirlo: poder recuperarlo sería poder "
                    + "demostrar que una denuncia anónima es tuya.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Presentada, con su código",
                    content = @Content(schema = @Schema(implementation = ComplaintCreatedResponse.class))),
            @ApiResponse(responseCode = "400", description = "Falta la categoría, la descripción o el 'anonima'",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('denuncia:crear')")
    public ComplaintCreatedResponse presentar(
            @Valid @RequestBody CreateComplaintRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return complaintService.presentar(request, usuario.getUser());
    }

    @Operation(summary = "Seguir una denuncia con su código",
            description = "Autenticado pero SIN comprobar identidad: el código es la "
                    + "credencial, y va en el cuerpo y no en la URL (ADR 034). Un código "
                    + "inexistente y uno de otra empresa dan el mismo 404, para no confirmar "
                    + "cuáles son válidos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El expediente y su conversación",
                    content = @Content(schema = @Schema(implementation = ComplaintResponse.class))),
            @ApiResponse(responseCode = "404", description = "No hay ninguna denuncia con ese código",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/seguimiento")
    @PreAuthorize("hasAuthority('denuncia:crear')")
    public ResponseEntity<ComplaintResponse> seguimientoConCodigo(
            @Valid @RequestBody ComplaintTrackingRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(complaintService.seguimiento(request.codigo(), usuario.getUser()));
    }

    @Operation(summary = "Responder en una denuncia con su código",
            description = "El código y el mensaje, en el cuerpo (ADR 034). El mensaje se guarda "
                    + "SIN autor si la denuncia es anónima, aunque quien escribe esté autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Expediente con el mensaje añadido",
                    content = @Content(schema = @Schema(implementation = ComplaintResponse.class))),
            @ApiResponse(responseCode = "400", description = "Falta el mensaje",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No hay ninguna denuncia con ese código",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El expediente está cerrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/seguimiento/mensajes")
    @PreAuthorize("hasAuthority('denuncia:crear')")
    public ResponseEntity<ComplaintResponse> responderConCodigo(
            @Valid @RequestBody ComplaintTrackingRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        if (request.texto() == null || request.texto().isBlank()) {
            throw new BusinessException("El mensaje no puede estar vacío.", HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(complaintService.responder(
                request.codigo(), new ComplaintMessageRequest(request.texto()), usuario.getUser()));
    }

    @Operation(summary = "Las denuncias que he presentado identificándome",
            description = "Las anónimas NO salen aquí y no pueden salir: no hay ningún dato "
                    + "que las relacione con quien las puso. A esas se llega solo con el código.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Listado",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ComplaintSummaryResponse.class)))))
    @GetMapping("/mias")
    @PreAuthorize("hasAuthority('denuncia:crear')")
    public ResponseEntity<List<ComplaintSummaryResponse>> mias(
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(complaintService.mias(usuario.getUser()));
    }

    @Operation(summary = "Un expediente propio, sin necesidad del código",
            description = "Solo para las que presentaste IDENTIFICÁNDOTE. Sobre una anónima "
                    + "devuelve 404 aunque sea tuya: el sistema no sabe que lo es, que es "
                    + "exactamente lo que se prometió. A esas se entra con el código.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El expediente y su conversación",
                    content = @Content(schema = @Schema(implementation = ComplaintResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe, no es tuya, o es anónima",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/mias/{id}")
    @PreAuthorize("hasAuthority('denuncia:crear')")
    public ResponseEntity<ComplaintResponse> miDetalle(
            @PathVariable long id,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(complaintService.miDetalle(id, usuario.getUser()));
    }

    @Operation(summary = "Responder en un expediente propio",
            description = "Mismo alcance que el anterior: solo las identificadas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Expediente con el mensaje añadido",
                    content = @Content(schema = @Schema(implementation = ComplaintResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe, no es tuya, o es anónima",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El expediente está cerrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/mias/{id}/mensajes")
    @PreAuthorize("hasAuthority('denuncia:crear')")
    public ResponseEntity<ComplaintResponse> responderComoDenunciante(
            @PathVariable long id,
            @Valid @RequestBody ComplaintMessageRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(
                complaintService.responderComoDenunciante(id, request, usuario.getUser()));
    }

    // ------------------------------------------------------------------
    // Instruir
    // ------------------------------------------------------------------

    @Operation(summary = "La bandeja del canal",
            description = "Las denuncias de la empresa, las abiertas primero y dentro de cada "
                    + "grupo las más antiguas arriba: son las que están más cerca de que se "
                    + "pase el plazo. Solo ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ComplaintSummaryResponse.class)))),
            @ApiResponse(responseCode = "403", description = "Sin permiso para instruir denuncias",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("hasAuthority('denuncia:instruir')")
    public ResponseEntity<List<ComplaintSummaryResponse>> bandeja(
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(complaintService.bandeja(usuario.getUser()));
    }

    @Operation(summary = "Un expediente completo", description = "Solo quien instruye: el Responsable del Sistema o, sin él, los ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El expediente y su conversación",
                    content = @Content(schema = @Schema(implementation = ComplaintResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe, o es de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('denuncia:instruir')")
    public ResponseEntity<ComplaintResponse> detalle(
            @PathVariable long id,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(complaintService.detalle(id, usuario.getUser()));
    }

    @Operation(summary = "Escribir en un expediente como instructor",
            description = "En las denuncias de antes del acuse automático (ADR 048), el primer "
                    + "mensaje vale como acuse de recibo. Nadie instruye una denuncia que presentó él mismo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Expediente con el mensaje añadido",
                    content = @Content(schema = @Schema(implementation = ComplaintResponse.class))),
            @ApiResponse(responseCode = "403", description = "Es una denuncia tuya",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe, o es de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El expediente está cerrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/mensajes")
    @PreAuthorize("hasAuthority('denuncia:instruir')")
    public ResponseEntity<ComplaintResponse> responderComoInstructor(
            @PathVariable long id,
            @Valid @RequestBody ComplaintMessageRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(
                complaintService.responderComoInstructor(id, request, usuario.getUser()));
    }

    @Operation(summary = "Mover una denuncia de estado",
            description = "Cerrarla (RESUELTA o ARCHIVADA) exige escribir la conclusión: la "
                    + "ley obliga a responder, no a dar la razón. Un expediente cerrado no se "
                    + "reabre por aquí.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Expediente actualizado",
                    content = @Content(schema = @Schema(implementation = ComplaintResponse.class))),
            @ApiResponse(responseCode = "400", description = "Se cierra sin conclusión, o se manda conclusión sin cerrar",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Es una denuncia tuya",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No existe, o es de otra empresa",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya estaba cerrada, o ya estaba en ese estado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('denuncia:instruir')")
    public ResponseEntity<ComplaintResponse> cambiarEstado(
            @PathVariable long id,
            @Valid @RequestBody UpdateComplaintStatusRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(
                complaintService.cambiarEstado(id, request, usuario.getUser()));
    }

    @Operation(summary = "Cómo funciona el canal de mi empresa",
            description = "Quién es el Responsable del Sistema (si la empresa lo ha designado) y cómo comunicar "
                    + "algo de palabra o pedir una reunión (Ley 2/2023, arts. 7.2 y 9.2).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El canal",
                    content = @Content(schema = @Schema(implementation = ComplaintChannelInfoResponse.class)))
    })
    @GetMapping("/canal")
    @PreAuthorize("hasAuthority('denuncia:crear')")
    public ResponseEntity<ComplaintChannelInfoResponse> canal(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(canalDeDenuncias.paraQuienDenuncia(usuario.getUser()));
    }

    @Operation(summary = "Quién ha abierto cada expediente",
            description = "Solo quien instruye: la Autoridad recomienda que este registro quede reservado al "
                    + "Responsable del Sistema (Recomendación 1/2026). Lo último primero, hasta 500.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Los accesos",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = DataAccessResponse.class))))
    })
    @GetMapping("/accesos")
    @PreAuthorize("hasAuthority('denuncia:instruir')")
    public ResponseEntity<List<DataAccessResponse>> accesos(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(registroDeAccesos.deLosExpedientes(usuario.getUser()));
    }

    @Operation(summary = "El libro-registro en CSV",
            description = "Ley 2/2023, art. 26: fecha de recepción, objeto, estado, hitos y fin de cada "
                    + "comunicación. Sin relatos, conclusiones ni nombres. La descarga queda anotada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El fichero",
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    })
    @GetMapping("/libro-registro.csv")
    @PreAuthorize("hasAuthority('denuncia:instruir')")
    public ResponseEntity<byte[]> libroRegistro(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"libro-registro.csv\"")
                .body(complaintService.libroRegistro(usuario.getUser()));
    }

    @Operation(summary = "Ampliar a seis meses el plazo de respuesta",
            description = "Ley 2/2023, art. 9.2.d: solo en casos de especial complejidad, una vez y antes de "
                    + "que venza. El motivo le llega a quien denunció como un mensaje del expediente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Expediente con el plazo ampliado",
                    content = @Content(schema = @Schema(implementation = ComplaintResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya se amplió, ya venció o está cerrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/prorroga")
    @PreAuthorize("hasAuthority('denuncia:instruir')")
    public ResponseEntity<ComplaintResponse> prorrogar(
            @PathVariable long id,
            @Valid @RequestBody ComplaintExtensionRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(complaintService.prorrogar(id, request, usuario.getUser()));
    }

    @Operation(summary = "Suprimir un texto del expediente",
            description = "La descripción (sin mensajeId) o un mensaje, porque contiene categorías especiales "
                    + "de datos, no es pertinente o se acreditó que no es veraz (Ley 2/2023, arts. 30 y 32). "
                    + "No se puede deshacer: queda la marca de cuándo y por qué.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Expediente con el texto suprimido",
                    content = @Content(schema = @Schema(implementation = ComplaintResponse.class))),
            @ApiResponse(responseCode = "404", description = "La denuncia o el mensaje no existen",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/supresiones")
    @PreAuthorize("hasAuthority('denuncia:instruir')")
    public ResponseEntity<ComplaintResponse> suprimir(
            @PathVariable long id,
            @Valid @RequestBody ComplaintRedactionRequest request,
            @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(complaintService.suprimirTexto(id, request, usuario.getUser()));
    }
}
