package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * El resultado de comprobar la traza de auditoría entera, para quien mantiene
 * el servicio (ADR 040).
 *
 * Es lo que {@link AuditIntegrityResponse} no le puede decir a una empresa: las
 * cifras de toda la instalación y, si la cadena está rota, <b>en qué fila y de
 * qué empresa</b>. A una empresa cuya traza se queda sin comprobar por una fila
 * ajena solo se le dice que avise a quien administra el servicio; ese es quien
 * lee esto.
 */
@Schema(description = "Resultado de comprobar la cadena de hashes de la auditoría, con las cifras de toda la instalación")
public record PlatformIntegrityResponse(

        @Schema(description = "Si la traza está intacta hasta donde se puede comprobar")
        boolean intacta,

        @Schema(description = "Movimientos revisados. Si está rota, los anteriores a la rotura")
        long movimientos,

        @Schema(description = "De esos, a cuántos se les ha recalculado el hash y cuadra")
        long comprobados,

        @Schema(description = "Movimientos antiguos de los que solo se ha podido comprobar el enlace con el anterior")
        long soloEnlace,

        @Schema(description = "Id del primer movimiento con problemas, si lo hay")
        Long primerFallo,

        @Schema(description = "De qué empresa es ese movimiento, si se sabe")
        Long empresaDelFallo,

        @Schema(description = "Qué le pasa a ese movimiento, en una frase")
        String motivo,

        @Schema(description = "Cuándo se hizo el recorrido. Vale medio minuto para quien lo pida después")
        Instant comprobadaEn
) {
}
