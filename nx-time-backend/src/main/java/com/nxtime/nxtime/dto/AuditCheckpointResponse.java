package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * La última comprobación automática de la cadena de auditoría (Fase A4).
 *
 * Existe para que la pantalla de auditoría pueda decir «comprobada anoche,
 * intacta» sin que nadie tenga que lanzar la verificación entera. Es la
 * diferencia entre poder demostrar la integridad y poder demostrarla
 * <em>cómodamente</em>, que en la práctica es la diferencia entre comprobarla y
 * no comprobarla nunca.
 */
@Schema(description = "Resultado de la última comprobación automática de la cadena de auditoría")
public record AuditCheckpointResponse(

        @Schema(description = "Cuándo se comprobó por última vez")
        Instant verificadoEn,

        @Schema(description = "Hasta qué movimiento de auditoría llegó")
        long hastaMovimiento,

        @Schema(description = "Movimientos revisados en total")
        long movimientos,

        @Schema(description = "De esos, a cuántos se les ha recalculado el hash")
        long comprobados,

        @Schema(description = "De esos, a cuántos solo se les ha podido comprobar el enlace (ver V26)")
        long soloEnlace,

        @Schema(description = "Movimientos escritos después de esa comprobación, todavía sin revisar")
        long pendientes
) {
}
