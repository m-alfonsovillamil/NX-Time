package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    // ------------------------------------------------------------------
    // Tramos: en qué proyecto se trabaja (ADR 017)
    // ------------------------------------------------------------------

    /** Los proyectos en los que puede fichar la persona ese día: asignación vigente y proyecto activo. */
    List<Project> proyectosParaFichar(
            User persona, LocalDate dia);

    /** El proyecto del tramo en curso de la jornada, si lo hay. */
    Optional<Project> proyectoEnCurso(TimeEntry registro);

    /** Abre el primer tramo de una jornada recién iniciada. */
    void abrirTramo(TimeEntry registro, Project proyecto);

    /**
     * Cierra el tramo en curso y abre otro en {@code proyecto} desde
     * {@code ahora}. Si la jornada no tenía tramos (se inició sin elegir,
     * p. ej. desde la app 1.3), el proyecto elegido cubre desde la entrada:
     * es la única lectura razonable de "estoy en este proyecto" sin más datos.
     */
    void cambiarDeProyecto(TimeEntry registro, Project proyecto, Instant ahora);

    /** Una pausa fichada acaba de terminar: sus segundos van al tramo en curso. */
    void sumarPausaAlTramo(TimeEntry registro, long segundos);

    // ------------------------------------------------------------------
    // Reparto a mano (ADR 017)
    // ------------------------------------------------------------------

    /**
     * Los proyectos entre los que se pueden repartir las horas de un día: los
     * que esa persona tenía asignados ese día, estén o no activos hoy. A
     * diferencia de {@link #proyectosParaFichar}, aquí sí entra un proyecto ya
     * cerrado: sus horas de entonces existieron.
     */
    List<Project> proyectosDelDia(User persona, LocalDate dia);

    /** Guarda un reparto hecho a mano. La suma tiene que ser el neto; lo comprueba quien llama. */
    void aplicarReparto(TimeEntry registro, java.util.Map<Project, Long> segundosPorProyecto);
}
