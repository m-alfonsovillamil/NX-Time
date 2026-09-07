package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ProjectAssignmentRequest;
import com.nxtime.nxtime.dto.ProjectAssignmentResponse;
import com.nxtime.nxtime.dto.ProjectDetailResponse;
import com.nxtime.nxtime.dto.ProjectHoursResponse;
import com.nxtime.nxtime.dto.ProjectRequest;
import com.nxtime.nxtime.dto.ProjectResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Proyectos y horas por proyecto (Fase D).
 *
 * La idea que ordena todo lo demás: <b>las horas de un día van al
 * proyecto que esa persona tenía asignado ESE día</b>. Por eso las
 * asignaciones llevan vigencia y no se borran al sacar a alguien — se
 * cierran con una fecha de fin.
 */
public interface ProjectService {

    List<ProjectResponse> listar(User actor);

    /** El proyecto con sus asignaciones y las horas del mes indicado. */
    ProjectDetailResponse detalle(long id, int anio, int mes, User actor);

    ProjectResponse crear(ProjectRequest request, User actor);

    ProjectResponse editar(long id, ProjectRequest request, User actor);

    /**
     * Cierra o reabre un proyecto. Va aparte de {@link #editar} para que
     * guardar un cambio de nombre no pueda reabrir sin querer algo que
     * estaba cerrado.
     */
    ProjectResponse cambiarEstado(long id, boolean activo, User actor);

    /** Falla con 409 si ya tiene asignaciones: primero hay que quitarlas. */
    void borrar(long id, User actor);

    /**
     * Asigna a alguien al proyecto.
     *
     * Falla con 409 si esa persona ya está en otro proyecto en alguno de
     * esos días: la base lo impide con un EXCLUDE, y el servicio lo
     * comprueba antes solo para poder decir en QUÉ proyecto está.
     */
    ProjectAssignmentResponse asignar(long proyectoId, ProjectAssignmentRequest request, User actor);

    /**
     * Saca a alguien del proyecto poniendo la fecha de fin.
     *
     * No borra la asignación a propósito: borrarla haría desaparecer sus
     * horas pasadas de ese proyecto.
     */
    ProjectAssignmentResponse finalizarAsignacion(long asignacionId, LocalDate fechaFin, User actor);

    /** Las asignaciones de una persona, para su perfil. */
    List<ProjectAssignmentResponse> asignacionesDe(long usuarioId, User actor);

    /** Horas por proyecto de toda la empresa en un mes, para el panel. */
    ProjectHoursResponse horasDelMes(int anio, int mes, User actor);
}
