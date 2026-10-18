package com.nxtime.nxtime.dto;

/**
 * Lo que necesita saber quien va a denunciar (Ley 2/2023, arts. 7.2 y 9.2; ADR 048).
 *
 * @param responsable el nombre del Responsable del Sistema, o null si la
 *     empresa no lo ha designado (entonces lo lleva la administración)
 * @param viaVerbal cómo comunicarlo de palabra o pedir una reunión, o null
 */
public record ComplaintChannelInfoResponse(String responsable, String viaVerbal) {
}
