package com.nxtime.nxtime.dto;

import java.time.Instant;

/**
 * Cómo está montado el canal de denuncias de la empresa, para el ADMIN (ADR 048).
 *
 * @param responsableId el Responsable del Sistema Interno de Información (Ley
 *     2/2023, art. 8), o null si no se ha designado: entonces instruyen los ADMIN
 * @param desde cuándo se le designó: la empresa tiene 10 días hábiles para
 *     comunicarlo a la Autoridad Independiente de Protección del Informante
 * @param viaVerbal cómo comunicar algo de palabra o pedir una reunión (arts. 7.2 y 9.2)
 */
public record ComplaintChannelSettingsResponse(Long responsableId, String responsable, Instant desde, String viaVerbal) {
}
