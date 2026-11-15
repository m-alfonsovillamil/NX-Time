package com.nxtime.nxtime.dto;

import java.util.List;

/**
 * Los justificantes de unas ausencias (ADR 062).
 *
 * @param activo si la empresa deja adjuntarlos; los que ya hay se siguen
 *   viendo aunque esté apagado
 * @param justificantes solo los que quien pregunta puede ver: los suyos, y
 *   los de los demás con {@code ausencia:justificante:leer}
 */
public record JustificantesResponse(boolean activo, List<JustificanteResponse> justificantes) {
}
