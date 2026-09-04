package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.NoticeResponse;
import com.nxtime.nxtime.dto.UnreadNoticeCountResponse;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avisos dentro de la aplicación (Fase A).
 *
 * Sin authority propia, y no por descuido: todo el mundo tiene avisos,
 * así que un "aviso:leer" lo tendrían los cuatro roles -- sería ninguna
 * restricción disfrazada de restricción. {@code isAuthenticated()} dice
 * la verdad. Lo que sí se comprueba, y en el servicio, es que el aviso
 * sea TUYO: ni siquiera un compañero de la misma empresa puede tocarlo.
 */
@RestController
@RequestMapping("/api/v1/avisos")
@Tag(name = "Avisos", description = "Avisos in-app del usuario autenticado: listado, contador de no leídos "
        + "y marcado como leído. Se generan desde los mismos eventos que envían los correos.")
@SecurityRequirement(name = "bearerAuth")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @Operation(summary = "Mis avisos", description = "Los 50 más recientes primero. Solo los propios.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado de avisos",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = NoticeResponse.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NoticeResponse>> getMyNotices(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(noticeService.getMisAvisos(usuario.getUser()));
    }

    @Operation(summary = "Cuántos avisos sin leer tengo",
            description = "Lo consume el contador de la campana, que se pide mucho más a menudo que la lista.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contador de no leídos",
                    content = @Content(schema = @Schema(implementation = UnreadNoticeCountResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/no-leidos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnreadNoticeCountResponse> countUnread(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(new UnreadNoticeCountResponse(noticeService.contarNoLeidos(usuario.getUser())));
    }

    @Operation(summary = "Marcar un aviso como leído",
            description = "Idempotente: marcarlo dos veces no es un error.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aviso marcado"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "El aviso es de otra persona",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Aviso no encontrado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}/leido")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(
            @PathVariable long id, @AuthenticationPrincipal SecurityUser usuario) {
        noticeService.marcarLeido(id, usuario.getUser());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Marcar todos mis avisos como leídos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avisos marcados"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/leer-todos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal SecurityUser usuario) {
        noticeService.marcarTodosLeidos(usuario.getUser());
        return ResponseEntity.ok().build();
    }
}
