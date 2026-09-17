package com.nxtime.nxtime.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Una solicitud de borrado de datos, tal como la ven la persona que la pidió
 * y quien la resuelve.
 *
 * @param bloqueos lo que hoy impide ejecutarla, en frases para leer ("Tiene
 *     una jornada abierta"). Vacía si se puede ejecutar ya. Se calcula en el
 *     servidor para que la app no tenga que conocer las reglas, y solo tiene
 *     sentido mientras está PENDIENTE: en las demás va vacía.
 */
public record DeletionResponse(
        long id,
        long usuarioId,
        String nombre,
        String email,
        String estado,
        String motivo,
        Instant creadaEn,
        String resueltaPor,
        Instant resueltaEn,
        String comentarioResolucion,
        LocalDate anonimizarDesde,
        List<String> bloqueos
) {
}
