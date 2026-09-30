package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.KioskDtos.KioskClockRequest;
import com.nxtime.nxtime.dto.KioskDtos.KioskClockResponse;
import com.nxtime.nxtime.dto.KioskDtos.KioskCredential;
import com.nxtime.nxtime.dto.KioskDtos.KioskIdentity;
import com.nxtime.nxtime.dto.KioskDtos.KioskInfo;
import com.nxtime.nxtime.dto.KioskDtos.KioskPerson;
import com.nxtime.nxtime.dto.KioskDtos.PairingStarted;
import com.nxtime.nxtime.dto.KioskDtos.PairingStatus;
import com.nxtime.nxtime.dto.KioskDtos.PairingStatusRequest;
import com.nxtime.nxtime.security.KioskPrincipal;
import com.nxtime.nxtime.service.KioskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que habla la tablet del kiosco (ADR 033).
 *
 * Fuera de {@code /api/v1} a propósito: lo llama un dispositivo y no una
 * persona, con su propio esquema de autenticación
 * ({@code Authorization: Kiosco <token>}, ver KioskAuthenticationFilter). Las
 * dos rutas de emparejar son públicas: la tablet todavía no tiene token. El
 * resto lo protege SecurityConfig por ruta con {@code kiosco:fichar}, y no un
 * {@code @PreAuthorize}, porque esa authority no es de ningún rol.
 */
@RestController
@RequestMapping("/kiosco")
@Tag(name = "Kiosco", description = "La tablet de fichaje compartida: emparejarla, identificarse y fichar. "
        + "Con 'Authorization: Kiosco <token>', salvo las dos de emparejar.")
public class KioskController {

    private final KioskService service;

    public KioskController(KioskService service) {
        this.service = service;
    }

    @Operation(summary = "Pedir un código para emparejar esta tablet",
            description = "Devuelve el código que la tablet enseña (8 caracteres, 10 minutos) y un secreto con el "
                    + "que preguntar por el estado. Limitado por IP.")
    @PostMapping("/emparejar")
    public ResponseEntity<PairingStarted> emparejar() {
        return ResponseEntity.ok(service.iniciarEmparejamiento());
    }

    @Operation(summary = "¿Me han emparejado ya?",
            description = "PENDIENTE hasta que un ADMIN teclea el código. LISTO trae el token del kiosco, y solo "
                    + "esa vez: después responde ENTREGADO. CADUCADO si pasaron los 10 minutos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado",
                    content = @Content(schema = @Schema(implementation = PairingStatus.class))),
            @ApiResponse(responseCode = "404", description = "Ese secreto no es de ningún emparejamiento",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/emparejar/estado")
    public ResponseEntity<PairingStatus> estado(@Valid @RequestBody PairingStatusRequest request) {
        return ResponseEntity.ok(service.estadoDelEmparejamiento(request.secreto()));
    }

    @Operation(summary = "Quién soy", description = "El nombre del kiosco, el de su empresa y su zona horaria.")
    @GetMapping("/yo")
    public ResponseEntity<KioskInfo> yo(@AuthenticationPrincipal KioskPrincipal kiosco) {
        return ResponseEntity.ok(service.info(kiosco.kiosco()));
    }

    @Operation(summary = "La lista para identificarse con el PIN",
            description = "Quien está de alta en la empresa y tiene PIN, por nombre. Sin correo.")
    @ApiResponse(responseCode = "200", description = "Lista",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = KioskPerson.class))))
    @GetMapping("/plantilla")
    public ResponseEntity<List<KioskPerson>> plantilla(@AuthenticationPrincipal KioskPrincipal kiosco) {
        return ResponseEntity.ok(service.plantilla(kiosco.kiosco()));
    }

    @Operation(summary = "Identificarse",
            description = "Con la tarjeta ('qr') o con 'usuarioId' y 'pin'. Devuelve en qué está su jornada y "
                    + "sus proyectos de hoy. No deja nada abierto: al fichar se manda otra vez la credencial.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identificada",
                    content = @Content(schema = @Schema(implementation = KioskIdentity.class))),
            @ApiResponse(responseCode = "400", description = "Sin credencial",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "PIN incorrecto",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "La tarjeta o la persona no valen en este kiosco",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "PIN bloqueado, o demasiados intentos en el kiosco",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/identificar")
    public ResponseEntity<KioskIdentity> identificar(
            @Valid @RequestBody KioskCredential credencial,
            @AuthenticationPrincipal KioskPrincipal kiosco) {
        return ResponseEntity.ok(service.identificar(kiosco.kiosco(), credencial));
    }

    @Operation(summary = "Fichar",
            description = "La misma credencial que al identificarse, y qué se ficha. Las mismas reglas que desde "
                    + "la app; en la auditoría queda dicho desde qué kiosco.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichado",
                    content = @Content(schema = @Schema(implementation = KioskClockResponse.class))),
            @ApiResponse(responseCode = "409", description = "No se puede en el estado de su jornada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/fichar")
    public ResponseEntity<KioskClockResponse> fichar(
            @Valid @RequestBody KioskClockRequest request,
            @AuthenticationPrincipal KioskPrincipal kiosco) {
        return ResponseEntity.ok(service.fichar(kiosco.kiosco(), request));
    }
}
