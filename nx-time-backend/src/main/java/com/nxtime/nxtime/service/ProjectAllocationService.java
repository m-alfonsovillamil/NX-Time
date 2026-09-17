package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.TimeEntry;

/**
 * Mantiene las imputaciones de horas a proyectos de cada jornada (ADR 017).
 *
 * <b>Invariante</b>: en una jornada cerrada con imputaciones, la suma de sus
 * segundos es el neto de la jornada (duración menos pausas). Es el único sitio
 * que escribe imputaciones, y quien cambia algo que mueve el neto tiene que
 * avisar aquí: cerrar, cambiar pausas o corregir.
 *
 * Cómo se reparte, por orden:
 * <ol>
 *   <li>Si alguien repartió a mano (origen MANUAL), se respeta y se reescala en
 *       proporción al nuevo neto.</li>
 *   <li>Si hay tramos (se eligió proyecto al fichar), sale de ellos: cada tramo
 *       menos sus pausas fichadas y menos lo que solapen las pausas añadidas.</li>
 *   <li>Si no, y la persona tenía un solo proyecto asignado ese día, todo va a
 *       ese. Es lo que hacían los informes antes de V23, y lo que sigue pasando
 *       con quien ficha desde una app que no pregunta.</li>
 *   <li>Con varios proyectos y sin tramos, no se imputa nada: inventarse un
 *       reparto sería peor que dejar las horas "sin proyecto".</li>
 * </ol>
 */
public interface ProjectAllocationService {

    /** La jornada se acaba de cerrar (salida fichada o cierre nocturno). */
    void alCerrar(TimeEntry registro);

    /** Han cambiado las pausas de la jornada (pausa añadida o deshecha). */
    void alCambiarPausas(TimeEntry registro);

    /**
     * Una corrección ha sustituido {@code original} por {@code corregido}.
     * Tramos e imputaciones se mudan a la versión nueva (si no, sus horas
     * desaparecerían de los informes) y el reparto se reescala al nuevo neto.
     */
    void alCorregir(TimeEntry original, TimeEntry corregido);
}
