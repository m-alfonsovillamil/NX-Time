package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotNull;

/** Activar o no los justificantes en las ausencias de la empresa (ADR 062). */
public record ActivarJustificantesRequest(@NotNull(message = "Di si se activan o no.") Boolean activo) {
}
