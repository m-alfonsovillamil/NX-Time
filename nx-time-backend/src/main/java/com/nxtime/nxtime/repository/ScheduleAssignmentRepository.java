package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ScheduleAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleAssignmentRepository extends JpaRepository<ScheduleAssignment, Long> {

    /** Todas las de una persona, de la más reciente a la más antigua. */
    @Query("SELECT a FROM asignaciones_horario a JOIN FETCH a.plantilla "
            + "WHERE a.usuario.id = :usuarioId ORDER BY a.fechaInicio DESC")
    List<ScheduleAssignment> findDeUsuario(@Param("usuarioId") long usuarioId);

    /** Las de una persona que tocan un rango (ambos extremos incluidos). */
    @Query("SELECT a FROM asignaciones_horario a JOIN FETCH a.plantilla "
            + "WHERE a.usuario.id = :usuarioId AND a.fechaInicio <= :hasta "
            + "AND (a.fechaFin IS NULL OR a.fechaFin >= :desde)")
    List<ScheduleAssignment> findDeUsuarioEnRango(
            @Param("usuarioId") long usuarioId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /** Las vigentes un día en una empresa, para el cuadrante del equipo. */
    @Query("SELECT a FROM asignaciones_horario a JOIN FETCH a.plantilla JOIN FETCH a.usuario "
            + "WHERE a.empresa.id = :empresaId AND a.fechaInicio <= :dia "
            + "AND (a.fechaFin IS NULL OR a.fechaFin >= :dia)")
    List<ScheduleAssignment> findVigentesDeEmpresaEl(
            @Param("empresaId") long empresaId, @Param("dia") LocalDate dia);

    /**
     * Quién tiene cuadrante en algún día del rango, de todas las empresas.
     *
     * Para el barrido nocturno de horas extra: con esto sabe, en UNA consulta,
     * a quién tiene que calcularle la semana con el horario teórico y a quién
     * le sigue valiendo la jornada contratada, que es el camino de siempre.
     */
    @Query("SELECT DISTINCT a.usuario.id FROM asignaciones_horario a "
            + "WHERE a.fechaInicio <= :hasta AND (a.fechaFin IS NULL OR a.fechaFin >= :desde)")
    Set<Long> findUsuarioIdsConCuadranteEnRango(
            @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /**
     * Si la plantilla ya ha estado en vigor para alguien antes de una fecha.
     *
     * Una plantilla así ya ha definido horas teóricas pasadas, y cambiar sus
     * tramos las reescribiría: es lo que la vigencia existe para impedir.
     */
    @Query("SELECT COUNT(a) > 0 FROM asignaciones_horario a "
            + "WHERE a.plantilla.id = :plantillaId AND a.fechaInicio < :fecha")
    boolean haEstadoEnVigorAntesDe(
            @Param("plantillaId") long plantillaId, @Param("fecha") LocalDate fecha);

    boolean existsByPlantilla_Id(long plantillaId);
}
