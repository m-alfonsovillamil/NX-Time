package com.nxtime.nxtime.dto;

/**
 * Lo que espera una decisión de quien pregunta: el contador de cada bandeja
 * del panel de gestión.
 *
 * Cada número es <b>exactamente</b> lo que esa persona vería al abrir la
 * bandeja correspondiente, no un agregado de la empresa: las correcciones
 * cuentan solo las que le toca resolver a ella, y las horas extra excluyen
 * las suyas propias. Un contador que dijera 3 y una bandeja que enseñara 2
 * sería peor que no tener contador.
 *
 * Un 0 también significa "no tienes permiso para esa bandeja".
 */
public record PendingWorkResponse(
        int ausencias,
        int correcciones,
        int horasExtra
) {
}
