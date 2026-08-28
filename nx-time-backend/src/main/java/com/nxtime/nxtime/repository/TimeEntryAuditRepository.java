package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.TimeEntryAudit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Solo lectura + inserción desde el código Java (ver TimeEntryAudit):
 * a propósito no hay ningún método de actualización o borrado aquí, ni
 * falta que haga -- la tabla en sí revoca esos permisos al rol de la
 * aplicación (ver V3__audit_trail.sql).
 */
public interface TimeEntryAuditRepository extends JpaRepository<TimeEntryAudit, Long> {

    /** Última fila insertada, para encadenar su hash con la siguiente (ver TimeEntryAuditListener). */
    Optional<TimeEntryAudit> findTopByOrderByIdDesc();

    /** Línea temporal completa de un fichaje, más antiguo primero. */
    List<TimeEntryAudit> findByRegistro_IdOrderByFechaHoraAsc(long registroId);
}
