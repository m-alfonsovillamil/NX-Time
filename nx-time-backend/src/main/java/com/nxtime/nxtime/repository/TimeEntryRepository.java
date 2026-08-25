package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    Optional<TimeEntry> findByUsuarioAndHoraSalidaIsNull(User usuario);

    /**
     * JOIN FETCH evita el N+1 al cargar el usuario de cada fichaje (ver
     * auditoría). Pageable acota el resultado -- antes /historial
     * devolvía la tabla entera sin límite.
     */
    @Query("SELECT t FROM registros t JOIN FETCH t.usuario WHERE t.usuario = :usuario ORDER BY t.horaEntrada DESC")
    List<TimeEntry> findHistoryByUsuario(@Param("usuario") User usuario, Pageable pageable);

    /**
     * Antes usaba findByEmpresa (TODOS los usuarios de la empresa,
     * incluidos otros gestores -- ver auditoría, hueco de aislamiento
     * multi-tenant). Aquí se filtra explícitamente por rol EMPLEADO, de
     * modo que "el historial del equipo" de un gestor sea solo el de
     * sus empleados. Filtra por t.empresa directo (denormalizado desde
     * la Fase 3), no por u.empresa: aprovecha el índice
     * (empresa_id, hora_entrada) de "registros" en vez de forzar el
     * join a "usuarios" para el filtro de tenant.
     */
    @Query("SELECT t FROM registros t JOIN FETCH t.usuario u "
            + "WHERE t.empresa = :empresa AND u.rol = com.nxtime.nxtime.domain.Role.EMPLEADO "
            + "ORDER BY t.horaEntrada DESC")
    List<TimeEntry> findTeamHistory(@Param("empresa") Company empresa, Pageable pageable);
}
