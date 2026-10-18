package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Las condiciones del servicio y el contrato de encargo de una empresa (ADR 046). */
@Schema(description = "Qué versión de las condiciones está en vigor y cuál tiene aceptada la empresa")
public record CompanyTermsResponse(
        @Schema(description = "La versión en vigor", example = "2026-10-18")
        String versionVigente,
        @Schema(description = "La que tiene aceptada la empresa. Null si ninguna")
        String versionAceptada,
        Instant aceptadaEn,
        @Schema(description = "Quién las aceptó en nombre de la empresa. Null si ninguna, o si ya no está")
        String aceptadaPor,
        @Schema(description = "Si falta aceptar la versión en vigor")
        boolean porAceptar
) {
}
