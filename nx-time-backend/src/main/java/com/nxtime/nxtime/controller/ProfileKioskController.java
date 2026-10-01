package com.nxtime.nxtime.controller;

import com.nxtime.nxtime.dto.KioskDtos.KioskCard;
import com.nxtime.nxtime.dto.KioskDtos.KioskPinRequest;
import com.nxtime.nxtime.dto.KioskDtos.MyKioskStatus;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.KioskProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * El PIN y la tarjeta de cada persona para fichar en un kiosco (ADR 033).
 *
 * Lo propio pide {@code fichaje:escribir}, que tiene todo el que ficha. Las
 * tarjetas de la plantilla, para imprimirlas, {@code empleado:gestionar}
 * (RRHH): con una tarjeta se puede fichar en nombre de su dueño en un kiosco,
 * así que no es algo que pueda sacar cualquier gestor.
 */
@RestController
@Tag(name = "Kiosco", description = "La tablet de fichaje compartida: emparejarla, identificarse y fichar. "
        + "Con 'Authorization: Kiosco <token>', salvo las dos de emparejar.")
@SecurityRequirement(name = "bearerAuth")
public class ProfileKioskController {

    private final KioskProfileService service;

    public ProfileKioskController(KioskProfileService service) {
        this.service = service;
    }

    @Operation(summary = "¿Puedo fichar en un kiosco?", description = "Si tengo PIN, si tengo tarjeta y si el PIN está bloqueado.")
    @GetMapping("/api/v1/perfil/kiosco")
    @PreAuthorize("hasAuthority('fichaje:escribir')")
    public ResponseEntity<MyKioskStatus> estado(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.estado(usuario.getUser()));
    }

    @Operation(summary = "Elegir mi PIN de kiosco",
            description = "De 4 a 6 cifras, sin repetidas ni seguidas (1111, 1234). Nadie más lo ve.")
    @PutMapping("/api/v1/perfil/kiosco/pin")
    @PreAuthorize("hasAuthority('fichaje:escribir')")
    public ResponseEntity<MyKioskStatus> fijarPin(
            @Valid @RequestBody KioskPinRequest request, @AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.fijarPin(usuario.getUser(), request.pin()));
    }

    @Operation(summary = "Quitar mi PIN de kiosco", description = "Dejo de salir en la lista del kiosco.")
    @DeleteMapping("/api/v1/perfil/kiosco/pin")
    @PreAuthorize("hasAuthority('fichaje:escribir')")
    public ResponseEntity<MyKioskStatus> quitarPin(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.quitarPin(usuario.getUser()));
    }

    @Operation(summary = "Mi tarjeta de kiosco", description = "El contenido del QR y el QR en SVG. Si no tenía, se crea.")
    @GetMapping("/api/v1/perfil/kiosco/tarjeta")
    @PreAuthorize("hasAuthority('fichaje:escribir')")
    public ResponseEntity<KioskCard> tarjeta(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.tarjeta(usuario.getUser()));
    }

    @Operation(summary = "Una tarjeta nueva", description = "La anterior, impresa o en el móvil, deja de valer.")
    @PostMapping("/api/v1/perfil/kiosco/tarjeta")
    @PreAuthorize("hasAuthority('fichaje:escribir')")
    public ResponseEntity<KioskCard> regenerar(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.regenerarTarjeta(usuario.getUser()));
    }

    @Operation(summary = "Las tarjetas de la plantilla, para imprimirlas",
            description = "De toda la gente de alta. A quien no tenía, se le crea.")
    @ApiResponse(responseCode = "200", description = "Tarjetas",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = KioskCard.class))))
    @GetMapping("/api/v1/gestor/kiosco/tarjetas")
    @PreAuthorize("hasAuthority('empleado:gestionar')")
    public ResponseEntity<List<KioskCard>> tarjetasDeLaEmpresa(@AuthenticationPrincipal SecurityUser usuario) {
        return ResponseEntity.ok(service.tarjetasDeLaEmpresa(usuario.getUser()));
    }
}
