package com.nxtime.nxtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * La instalación de un vistazo, para quien la mantiene (ADR 040).
 *
 * @param tareas lo mismo que sirve {@code /estado/tareas}
 */
@Schema(description = "Los totales de la instalación, el estado de las tareas nocturnas y el de la traza de auditoría")
public record PlatformSummaryResponse(
        long empresas,
        @Schema(description = "Empresas en las que alguien ha fichado en los últimos 30 días")
        long empresasConActividad,
        @Schema(description = "Cuentas en activo, de todas las empresas y de todos los roles")
        long empleadosActivos,
        @Schema(description = "Registros de empresa que se quedaron sin confirmar el correo")
        long registrosSinConfirmar,
        @Schema(description = "Fichajes empezados hoy (día de Madrid), sin los anulados")
        long fichajesHoy,
        @Schema(description = "Altas de empresas de las últimas doce semanas, de la más antigua a la actual")
        List<Altas> altasPorSemana,
        SystemStatusResponse tareas,
        Cadena cadena
) {

    /**
     * @param semana el lunes en que empieza, en Madrid
     */
    public record Altas(LocalDate semana, long altas) {
    }

    /**
     * La traza de auditoría según la comprobación automática de cada noche.
     *
     * @param ultimaComprobacion     null si todavía no ha habido ninguna que saliera bien
     * @param movimientosComprobados cuántos entraron en ella
     * @param movimientosSinRevisar  cuántos se han escrito después
     */
    public record Cadena(Instant ultimaComprobacion, long movimientosComprobados, long movimientosSinRevisar) {
    }
}
