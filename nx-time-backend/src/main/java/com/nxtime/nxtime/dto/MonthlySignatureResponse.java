package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.MonthlySignatureStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Una firma mensual (Fase B3).
 *
 * @param hash el SHA-256 de lo que se firmó. Se enseña entero: es lo que
 *   permite a cualquiera comprobar después que el PDF dice lo mismo.
 * @param segundosNetos lo trabajado que se firmó, neto de pausas.
 */
public record MonthlySignatureResponse(
        long id,
        long usuarioId,
        String usuario,
        int anio,
        int mes,
        MonthlySignatureStatus estado,
        String hash,
        int jornadas,
        long segundosNetos,
        Instant firmadaEn,
        @Schema(nullable = true) Instant invalidadaEn,
        @Schema(nullable = true) String motivoInvalidacion,
        @Schema(nullable = true) String visadaPor,
        @Schema(nullable = true) Instant visadaEn
) {
}
