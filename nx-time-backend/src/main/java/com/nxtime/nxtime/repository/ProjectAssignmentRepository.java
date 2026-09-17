package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ProjectAssignment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {

    /**
     * Quién ha pasado por un proyecto, de la asignación más reciente a la
     * más antigua. El {@code JOIN FETCH} evita el N+1 al pintar el
     * nombre de cada persona.
     */
    @Query("SELECT a FROM asignaciones_proyecto a JOIN FETCH a.usuario "
            + "WHERE a.proyecto.id = :proyectoId ORDER BY a.fechaInicio DESC")
    List<ProjectAssignment> findDelProyecto(@Param("proyectoId") long proyectoId);

    /**
     * Las asignaciones vigentes de una persona un día concreto.
     *
     * Desde V23 puede haber varias (una por proyecto): la base solo impide
     * estar dos veces en el MISMO proyecto a la vez.
     */
    @Query("SELECT a FROM asignaciones_proyecto a JOIN FETCH a.proyecto "
            + "WHERE a.usuario.id = :usuarioId AND a.fechaInicio <= :dia "
            + "AND (a.fechaFin IS NULL OR a.fechaFin >= :dia) ORDER BY a.proyecto.codigo")
    List<ProjectAssignment> findVigentesDe(
            @Param("usuarioId") long usuarioId, @Param("dia") LocalDate dia);

    /** La asignación de una persona a UN proyecto que cubre un día, si la hay. */
    @Query("SELECT a FROM asignaciones_proyecto a JOIN FETCH a.proyecto "
            + "WHERE a.usuario.id = :usuarioId AND a.proyecto.id = :proyectoId "
            + "AND a.fechaInicio <= :dia AND (a.fechaFin IS NULL OR a.fechaFin >= :dia)")
    Optional<ProjectAssignment> findVigenteDelProyecto(
            @Param("usuarioId") long usuarioId,
            @Param("proyectoId") long proyectoId,
            @Param("dia") LocalDate dia);

    /** Todas las de una persona, para su perfil y para el historial. */
    @Query("SELECT a FROM asignaciones_proyecto a JOIN FETCH a.proyecto "
            + "WHERE a.usuario.id = :usuarioId ORDER BY a.fechaInicio DESC")
    List<ProjectAssignment> findDeUsuario(@Param("usuarioId") long usuarioId);

    long countByProyecto_Id(long proyectoId);

    /** Proyección de {@code ProjectAllocationRepository.sumarSegundosPorProyecto}. */
    interface ProjectHoursProjection {
        long getProyectoId();

        String getCodigo();

        String getNombre();

        long getSegundos();
    }

    /** Proyección de {@code ProjectAllocationRepository.sumarSegundosDelProyectoPorEmpleado}. */
    interface EmployeeProjectHoursProjection {
        long getUsuarioId();

        String getNombre();

        long getSegundos();
    }
}
