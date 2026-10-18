package com.nxtime.nxtime.dto;

import java.time.Instant;

/**
 * La baja de la empresa (ADR 050).
 *
 * @param solicitadaEn cuándo la pidió el ADMIN, o null si no hay ninguna
 * @param borradoDesde a partir de cuándo la tarea nocturna la borra entera
 */
public record CompanyClosureResponse(Instant solicitadaEn, Instant borradoDesde) {
}
