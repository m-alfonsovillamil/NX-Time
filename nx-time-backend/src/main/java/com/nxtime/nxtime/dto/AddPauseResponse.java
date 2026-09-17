package com.nxtime.nxtime.dto;

/**
 * Lo que ha pasado al añadir una pausa (ADR 015).
 *
 * Es el servidor quien decide si se aplica o se pide, así que la respuesta
 * tiene que decir cuál de las dos ha sido. Va uno de los dos objetos, nunca
 * los dos:
 *
 * @param aplicada si el tiempo trabajado YA ha cambiado
 * @param fichaje  el fichaje actualizado, cuando se aplicó directamente
 * @param correccion la solicitud creada, cuando la pausa era de un día
 *     pasado. Puede venir ya APROBADA si quien la pide puede aprobarse a sí
 *     mismo (ver ADR 010).
 */
public record AddPauseResponse(
        boolean aplicada,
        TimeEntryResponse fichaje,
        CorrectionResponse correccion
) {
}
