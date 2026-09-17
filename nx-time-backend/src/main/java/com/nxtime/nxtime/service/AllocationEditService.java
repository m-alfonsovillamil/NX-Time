package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AllocationsResponse;
import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.SetAllocationsRequest;

/**
 * Repartir a mano las horas de una jornada entre proyectos (ADR 017).
 *
 * Tres caminos, según lo que sume el reparto y de cuándo sea la jornada:
 *
 * <ul>
 *   <li><b>Suma el neto y es de esta semana</b>: se aplica en el acto. Mover
 *       horas entre proyectos no cambia cuánto se trabajó.</li>
 *   <li><b>Suma el neto pero es de una semana anterior</b>: se pide, y lo
 *       aprueba un gestor. Un mes ya informado no cambia de reparto sin que
 *       nadie lo mire.</li>
 *   <li><b>Suma más que el neto</b>: eso no es repartir, es decir que se
 *       trabajó más. Va por el mismo circuito de siempre —una corrección de
 *       horas— con el reparto dentro, y se aplican las dos cosas al aprobar.</li>
 * </ul>
 *
 * Sumar menos que el neto se rechaza: quitar horas también es corregir el
 * fichaje, y eso no es autoservicio.
 */
public interface AllocationEditService {

    /** El reparto actual de la jornada y con qué proyectos se puede repartir. */
    AllocationsResponse deLaJornada(long fichajeId, User actor);

    /**
     * @param aplicado el reparto ya aplicado, o null si hizo falta pedirlo.
     * @param solicitud la solicitud creada, o null si se aplicó directamente.
     */
    record Resultado(AllocationsResponse aplicado, CorrectionResponse solicitud) {
    }

    Resultado repartir(long fichajeId, SetAllocationsRequest peticion, User actor);
}
