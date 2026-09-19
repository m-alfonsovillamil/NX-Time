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

    /**
     * Toda la traza en orden de escritura, para comprobar la cadena.
     *
     * Por id y no por fecha: el id es el orden en que se escribieron, que es
     * el orden en que se encadenaron los hashes. Dos filas de la misma
     * milésima se ordenarían al azar por fecha y la cadena parecería rota.
     */
    List<TimeEntryAudit> findAllByOrderByIdAsc();

    /** Línea temporal completa de un fichaje, más antiguo primero. */
    List<TimeEntryAudit> findByRegistro_IdOrderByFechaHoraAsc(long registroId);

    /**
     * Línea temporal de VARIOS fichajes a la vez, más antiguo primero.
     *
     * Hace falta porque una corrección (Fase 8) no sobrescribe: anula el
     * fichaje original y crea uno nuevo. Preguntar solo por un id
     * devolvería media historia -- ver
     * {@code TimeEntryServiceImpl#getAuditTrail}.
     */
    List<TimeEntryAudit> findByRegistro_IdInOrderByFechaHoraAsc(List<Long> registroIds);
}
