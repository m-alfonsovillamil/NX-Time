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
     * La asignación de una persona en un día concreto, si la hay.
     *
     * Devuelve como mucho una porque la base impide el solape
     * ({@code ex_asignaciones_sin_solape}); si alguna vez devolviera dos,
     * la restricción se habría caído y es mejor enterarse por la
     * excepción de "resultado no único" que seguir sumando horas mal.
     */
    @Query("SELECT a FROM asignaciones_proyecto a JOIN FETCH a.proyecto "
            + "WHERE a.usuario.id = :usuarioId AND a.fechaInicio <= :dia "
            + "AND (a.fechaFin IS NULL OR a.fechaFin >= :dia)")
    Optional<ProjectAssignment> findVigenteDe(
            @Param("usuarioId") long usuarioId, @Param("dia") LocalDate dia);

    /** Todas las de una persona, para su perfil y para el historial. */
    @Query("SELECT a FROM asignaciones_proyecto a JOIN FETCH a.proyecto "
            + "WHERE a.usuario.id = :usuarioId ORDER BY a.fechaInicio DESC")
    List<ProjectAssignment> findDeUsuario(@Param("usuarioId") long usuarioId);

    long countByProyecto_Id(long proyectoId);

    /**
     * Horas por proyecto en un rango, agregadas en la base de datos.
     *
     * <b>Es la consulta que da sentido a toda la fase</b>, y tiene dos
     * cosas delicadas:
     *
     * <p><b>1. El JOIN es por rango de fechas, no por igualdad.</b> Cada
     * jornada se une con la asignación que esa persona tenía VIGENTE ese
     * día. Es lo que hace que mover a alguien de proyecto no recoloque su
     * pasado: sus horas de enero siguen contando en el proyecto en el que
     * estaba en enero. Un {@code usuarios.proyecto_id} habría hecho este
     * JOIN trivial y la respuesta falsa.
     *
     * <p><b>2. El día de una jornada es el día ESPAÑOL.</b>
     * {@code hora_entrada} es {@code TIMESTAMPTZ} en UTC (ADR 002), así
     * que hay que proyectarla a {@code Europe/Madrid} antes de quedarse
     * con la fecha. Sin eso, una jornada del 1 de julio que empieza a las
     * 00:30 hora española es todavía 30 de junio en UTC, y en un relevo
     * de proyecto a fin de mes se imputaría al proyecto equivocado.
     *
     * <p>Se toma la fecha de la ENTRADA y no de la salida, igual que
     * hacen los informes ({@code findParaInforme}): una jornada nocturna
     * pertenece al día en que empieza, no al día en que se cierra.
     *
     * <p>Nativa por lo mismo que los agregados del dashboard (Fase 10):
     * JPQL no sabe restar dos instantes.
     */
    @Query(value = """
            SELECT p.id     AS proyectoId,
                   p.codigo AS codigo,
                   p.nombre AS nombre,
                   COALESCE(SUM(
                       EXTRACT(EPOCH FROM (r.hora_salida - r.hora_entrada))
                       - r.segundos_pausa_acumulados), 0) AS segundos
            FROM registros r
            JOIN asignaciones_proyecto a
              ON a.usuario_id = r.usuario_id
             AND (r.hora_entrada AT TIME ZONE 'Europe/Madrid')::date >= a.fecha_inicio
             AND (r.hora_entrada AT TIME ZONE 'Europe/Madrid')::date
                 <= COALESCE(a.fecha_fin, DATE 'infinity')
            JOIN proyectos p ON p.id = a.proyecto_id
            WHERE r.empresa_id = :empresaId
              AND r.anulado = false
              AND r.hora_salida IS NOT NULL
              AND r.hora_entrada >= :desde
              AND r.hora_entrada < :hasta
            GROUP BY p.id, p.codigo, p.nombre
            ORDER BY segundos DESC
            """, nativeQuery = true)
    List<ProjectHoursProjection> sumarSegundosPorProyecto(
            @Param("empresaId") long empresaId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /**
     * Lo mismo para un único proyecto, repartido por persona: es lo que
     * se ve al abrir el proyecto.
     */
    @Query(value = """
            SELECT u.id     AS usuarioId,
                   u.nombre AS nombre,
                   COALESCE(SUM(
                       EXTRACT(EPOCH FROM (r.hora_salida - r.hora_entrada))
                       - r.segundos_pausa_acumulados), 0) AS segundos
            FROM registros r
            JOIN asignaciones_proyecto a
              ON a.usuario_id = r.usuario_id
             AND (r.hora_entrada AT TIME ZONE 'Europe/Madrid')::date >= a.fecha_inicio
             AND (r.hora_entrada AT TIME ZONE 'Europe/Madrid')::date
                 <= COALESCE(a.fecha_fin, DATE 'infinity')
            JOIN usuarios u ON u.id = r.usuario_id
            WHERE a.proyecto_id = :proyectoId
              AND r.anulado = false
              AND r.hora_salida IS NOT NULL
              AND r.hora_entrada >= :desde
              AND r.hora_entrada < :hasta
            GROUP BY u.id, u.nombre
            ORDER BY segundos DESC
            """, nativeQuery = true)
    List<EmployeeProjectHoursProjection> sumarSegundosDelProyectoPorEmpleado(
            @Param("proyectoId") long proyectoId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /** Proyección de {@link #sumarSegundosPorProyecto}. */
    interface ProjectHoursProjection {
        long getProyectoId();

        String getCodigo();

        String getNombre();

        long getSegundos();
    }

    /** Proyección de {@link #sumarSegundosDelProyectoPorEmpleado}. */
    interface EmployeeProjectHoursProjection {
        long getUsuarioId();

        String getNombre();

        long getSegundos();
    }
}
