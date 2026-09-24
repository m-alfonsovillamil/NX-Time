package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ScheduleIncident;
import com.nxtime.nxtime.domain.ScheduleIncidentStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleIncidentRepository extends JpaRepository<ScheduleIncident, Long> {

    /** Las de un día, para que el barrido sepa qué ya existe y qué retirar. */
    @Query("SELECT i FROM incidencias_cuadrante i JOIN FETCH i.usuario WHERE i.fecha = :fecha")
    List<ScheduleIncident> findDelDia(@Param("fecha") LocalDate fecha);

    /** Las de una persona en un año, de la más reciente a la más antigua. */
    @Query("SELECT i FROM incidencias_cuadrante i WHERE i.usuario.id = :usuarioId "
            + "AND i.fecha BETWEEN :desde AND :hasta ORDER BY i.fecha DESC, i.tipo")
    List<ScheduleIncident> findDeUsuarioEnRango(
            @Param("usuarioId") long usuarioId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * La bandeja de quien revisa: las de su empresa en esos estados, sin las
     * suyas. Nadie revisa lo suyo, y filtrarlo aquí y no en la pantalla es lo
     * que lo hace verdad para cualquier cliente.
     */
    @Query("SELECT i FROM incidencias_cuadrante i JOIN FETCH i.usuario "
            + "WHERE i.empresa.id = :empresaId AND i.usuario.id <> :excluido AND i.estado IN :estados "
            + "ORDER BY i.fecha DESC, i.usuario.nombre")
    List<ScheduleIncident> findBandeja(
            @Param("empresaId") long empresaId,
            @Param("excluido") long excluido,
            @Param("estados") Collection<ScheduleIncidentStatus> estados,
            org.springframework.data.domain.Pageable limite);

    /** Para la exportación de datos personales. */
    List<ScheduleIncident> findByUsuario_IdOrderByFechaDesc(long usuarioId);
}
