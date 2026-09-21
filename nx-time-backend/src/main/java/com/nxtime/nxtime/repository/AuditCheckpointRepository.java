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
}
