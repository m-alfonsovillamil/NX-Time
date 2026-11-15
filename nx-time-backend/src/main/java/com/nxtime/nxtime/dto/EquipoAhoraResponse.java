package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.Modalidad;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Quién está trabajando ahora y quién no ha fichado hoy (plan N, paso N7).
 *
 * Es una foto de este momento, para quien lleva un equipo: cuántos hay en cada
 * estado y, persona a persona, desde cuándo. No lleva la ubicación (ADR 056):
 * para saber dónde se fichó está el historial, que la enseña solo cuando la
 * empresa la pide.
 */
public record EquipoAhoraResponse(
        @Schema(description = "El día de hoy en la zona de la empresa") LocalDate fecha,
        int trabajando,
        int enPausa,
        int terminada,
        int sinFichar,
        @Schema(description = "De ellos, quienes ya deberían haber entrado según su cuadrante") int conRetraso,
        int noLaborable,
        List<PersonaAhora> personas
) {

    public enum EstadoAhora {
        TRABAJANDO,
        EN_PAUSA,
        /** Ya ha cerrado la jornada de hoy. */
        TERMINADA,
        SIN_FICHAR,
        /** Hoy no le toca: festivo, vacaciones, una ausencia aprobada o fin de semana. */
        NO_LABORABLE
    }

    /**
     * @param desde          la entrada de la jornada abierta, o la primera de hoy si ya terminó.
     * @param minutosHoy     tiempo efectivo de hoy hasta este momento (los de la jornada abierta,
     *                       si empezó ayer: un turno de noche).
     * @param motivo         por qué no es laborable, si no lo es.
     * @param entradaPrevista la hora de entrada de su cuadrante para hoy, si tiene.
     * @param conRetraso     sin fichar y pasados diez minutos de su entrada prevista.
     */
    public record PersonaAhora(
            long usuarioId,
            String nombre,
            EstadoAhora estado,
            @Schema(nullable = true) Instant desde,
            long minutosHoy,
            @Schema(nullable = true) String motivo,
            @Schema(nullable = true, example = "09:00") String entradaPrevista,
            boolean conRetraso,
            @Schema(nullable = true) Modalidad modalidad
    ) {
    }
}
