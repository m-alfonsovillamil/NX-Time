package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.PlatformAction;
import java.time.Instant;

/** Una línea del historial de una empresa en el panel de plataforma (ADR 046). */
public record PlatformActionDTO(Instant en, String operador, PlatformAction.Tipo accion, String motivo) {

    public static PlatformActionDTO de(PlatformAction accion) {
        return new PlatformActionDTO(accion.getEn(), accion.getOperadorEmail(), accion.getAccion(), accion.getMotivo());
    }
}
