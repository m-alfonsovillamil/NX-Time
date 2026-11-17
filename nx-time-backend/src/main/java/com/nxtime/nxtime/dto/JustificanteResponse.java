package com.nxtime.nxtime.dto;

import java.time.Instant;

/** El justificante de una ausencia, sin los bytes (ADR 062). */
public record JustificanteResponse(long peticionId, String nombre, String mime, long tamanoBytes, Instant subidoEn) {
}
