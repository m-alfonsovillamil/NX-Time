package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.AuditCheckpoint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Solo lectura e inserción, como {@link TimeEntryAuditRepository} y por el
 * mismo motivo: V28 le revoca UPDATE y DELETE al rol de la aplicación, así que
 * un método de actualización aquí no funcionaría aunque alguien lo escribiera.
 */
public interface AuditCheckpointRepository extends JpaRepository<AuditCheckpoint, Long> {

    /**
     * El punto de control más avanzado, que es desde donde sigue la próxima
     * verificación.
     *
     * Por {@code hastaId} y no por fecha: dos comprobaciones de la misma
     * milésima se ordenarían al azar por fecha, y la que se escogiera podría
     * ser la que llegó menos lejos.
     */
    Optional<AuditCheckpoint> findTopByOrderByHastaIdDesc();

    /** El último del tramo común (V46, ADR 050). */
    Optional<AuditCheckpoint> findTopByEmpresaIdIsNullOrderByHastaIdDesc();

    /** El último de la cadena de una empresa. */
    Optional<AuditCheckpoint> findTopByEmpresaIdOrderByHastaIdDesc(long empresaId);

    /** El más reciente de todos, sea de la cadena que sea: «comprobada anoche». */
    Optional<AuditCheckpoint> findTopByOrderByVerificadoEnDesc();

    /** El último de cada cadena, para sumar lo comprobado de la instalación entera. */
    @org.springframework.data.jpa.repository.Query("""
            select p from puntos_control_auditoria p
             where p.hastaId = (select max(q.hastaId) from puntos_control_auditoria q
                                 where (q.empresaId = p.empresaId) or (q.empresaId is null and p.empresaId is null))
            """)
    java.util.List<AuditCheckpoint> ultimosDeCadaCadena();
}
