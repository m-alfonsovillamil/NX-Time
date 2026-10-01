package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Kiosk;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface KioskRepository extends JpaRepository<Kiosk, Long> {

    Optional<Kiosk> findByTokenHash(String tokenHash);

    /** Los de una empresa, los que funcionan y los revocados, el más reciente primero. */
    List<Kiosk> findByEmpresa_IdOrderByCreadoEnDesc(long empresaId);

    /**
     * Cuándo fichó alguien en él por última vez, para que el ADMIN vea si una
     * tablet sigue en uso. Un UPDATE suelto y no cargar y guardar la entidad: la
     * que trae el filtro de seguridad viene de fuera de cualquier transacción.
     */
    @Transactional
    @Modifying
    @Query("UPDATE kioscos k SET k.ultimoUso = :cuando WHERE k.id = :id")
    int anotarUso(@Param("id") long id, @Param("cuando") Instant cuando);
}
