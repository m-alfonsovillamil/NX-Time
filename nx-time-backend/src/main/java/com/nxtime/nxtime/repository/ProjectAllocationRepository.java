package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ProjectAllocation;
import com.nxtime.nxtime.domain.TimeEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectAllocationRepository extends JpaRepository<ProjectAllocation, Long> {

    List<ProjectAllocation> findByRegistroOrderByIdAsc(TimeEntry registro);

    /**
     * Horas por proyecto en un rango, agregadas en la base.
     *
     * Desde V23 se SUMAN las imputaciones en vez de deducirlas uniendo la
     * jornada con la asignación de ese día (ADR 017). Lo que no cambia, y
     * sigue siendo delicado:
     * <ul>
     *   <li>El rango filtra por la ENTRADA de la jornada: una nocturna cuenta
     *       en el día en que empieza, como en el resto de informes.</li>
     *   <li>Solo jornadas vivas: al corregir, las imputaciones se mudan a la
     *       versión nueva y la anulada deja de contar.</li>
     * </ul>
     */
    @Query(value = """
            SELECT p.id     AS proyectoId,
                   p.codigo AS codigo,
                   p.nombre AS nombre,
                   COALESCE(SUM(i.segundos), 0) AS segundos
            FROM imputaciones_proyecto i
            JOIN registros r ON r.id = i.registro_id
            JOIN proyectos p ON p.id = i.proyecto_id
            WHERE i.empresa_id = :empresaId
              AND r.anulado = false
              AND r.hora_entrada >= :desde
              AND r.hora_entrada < :hasta
            GROUP BY p.id, p.codigo, p.nombre
            ORDER BY segundos DESC
            """, nativeQuery = true)
    List<ProjectAssignmentRepository.ProjectHoursProjection> sumarSegundosPorProyecto(
            @Param("empresaId") long empresaId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /** Lo mismo para un único proyecto, repartido por persona. */
    @Query(value = """
            SELECT u.id     AS usuarioId,
                   u.nombre AS nombre,
                   COALESCE(SUM(i.segundos), 0) AS segundos
            FROM imputaciones_proyecto i
            JOIN registros r ON r.id = i.registro_id
            JOIN usuarios u ON u.id = r.usuario_id
            WHERE i.proyecto_id = :proyectoId
              AND r.anulado = false
              AND r.hora_entrada >= :desde
              AND r.hora_entrada < :hasta
            GROUP BY u.id, u.nombre
            ORDER BY segundos DESC
            """, nativeQuery = true)
    List<ProjectAssignmentRepository.EmployeeProjectHoursProjection> sumarSegundosDelProyectoPorEmpleado(
            @Param("proyectoId") long proyectoId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);
}
