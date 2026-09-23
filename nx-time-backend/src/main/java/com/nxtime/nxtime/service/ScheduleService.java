package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ScheduleAssignmentRequest;
import com.nxtime.nxtime.dto.ScheduleAssignmentResponse;
import com.nxtime.nxtime.dto.ScheduleExceptionRequest;
import com.nxtime.nxtime.dto.ScheduleExceptionResponse;
import com.nxtime.nxtime.dto.ScheduleTemplateRequest;
import com.nxtime.nxtime.dto.ScheduleTemplateResponse;
import com.nxtime.nxtime.dto.TeamScheduleEntryResponse;
import com.nxtime.nxtime.dto.TheoreticalDayResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Cuadrantes: plantillas, asignaciones con vigencia y excepciones (Fase B1).
 *
 * <b>La regla que atraviesa todo el servicio:</b> nada de lo que se haga aquí
 * puede cambiar el horario teórico de un día ya pasado. Febrero ya está
 * informado, auditado y, cuando exista la firma mensual, firmado. Por eso:
 *
 * <ul>
 *   <li>una asignación no puede empezar antes de hoy, ni cerrarse antes de
 *       ayer;</li>
 *   <li>los tramos de una plantilla que ya se ha aplicado a algún día pasado
 *       no se pueden cambiar: se crea otra y se asigna desde la fecha que
 *       sea;</li>
 *   <li>una excepción solo puede ponerse o quitarse de hoy en adelante.</li>
 * </ul>
 *
 * Lo de ayer que salió distinto de lo previsto no se arregla reescribiendo el
 * cuadrante: queda como está, y lo que haya que explicar se explica.
 */
public interface ScheduleService {

    List<ScheduleTemplateResponse> plantillas(User actor);

    ScheduleTemplateResponse crearPlantilla(ScheduleTemplateRequest request, User actor);

    ScheduleTemplateResponse editarPlantilla(long plantillaId, ScheduleTemplateRequest request, User actor);

    void borrarPlantilla(long plantillaId, User actor);

    ScheduleAssignmentResponse asignar(ScheduleAssignmentRequest request, User actor);

    ScheduleAssignmentResponse cerrarAsignacion(long asignacionId, LocalDate fechaFin, User actor);

    /** Solo si todavía no ha empezado, o empieza hoy: es deshacer un error, no reescribir. */
    void borrarAsignacion(long asignacionId, User actor);

    List<ScheduleAssignmentResponse> asignacionesDe(long usuarioId, User actor);

    List<ScheduleExceptionResponse> crearExcepcion(ScheduleExceptionRequest request, User actor);

    void borrarExcepcion(long excepcionId, User actor);

    List<ScheduleExceptionResponse> excepcionesDe(long usuarioId, User actor);

    /** El horario teórico propio, día a día. */
    List<TheoreticalDayResponse> mio(LocalDate desde, LocalDate hasta, User actor);

    /** El de otra persona de la misma empresa, para quien gestiona los cuadrantes. */
    List<TheoreticalDayResponse> deUnaPersona(long usuarioId, LocalDate desde, LocalDate hasta, User actor);

    /** Quién tiene cuadrante un día en la empresa, y qué le toca. */
    List<TeamScheduleEntryResponse> equipo(LocalDate fecha, User actor);
}
