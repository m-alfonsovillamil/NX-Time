package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Los correos de alta que esperan (ADR 052). Salen como mucho 50 al día por
 * empresa, y sin pasar del tope diario del proveedor de correo.
 */
@Schema(description = "Las invitaciones de alta de la empresa")
public record EstadoInvitacionesResponse(
        @Schema(description = "Cuentas creadas a las que aún no les ha llegado el correo de alta") long pendientes,
        @Schema(description = "Cuántas pueden salir todavía hoy") int cupoHoy,
        @Schema(description = "Cuántas se están mandando ahora mismo, en segundo plano") int enCamino) {
}
