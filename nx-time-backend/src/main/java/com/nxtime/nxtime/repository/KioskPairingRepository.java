package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.KioskPairing;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KioskPairingRepository extends JpaRepository<KioskPairing, Long> {

    Optional<KioskPairing> findBySecretoHash(String secretoHash);

    /** Los que tienen ese código y todavía esperan a un ADMIN: sin kiosco y sin caducar. */
    @Query("SELECT p FROM kiosco_emparejamientos p WHERE p.codigoHash = :codigoHash "
            + "AND p.kiosco IS NULL AND p.caducaEn > :ahora")
    List<KioskPairing> findPendientes(@Param("codigoHash") String codigoHash, @Param("ahora") Instant ahora);

    /**
     * Se barren al pedir uno nuevo, no con una tarea programada (la cuota de
     * Neon): los caducados de hace más de un día ya no sirven ni para
     * contestar a la tablet que su código caducó.
     */
    @Modifying
    @Query("DELETE FROM kiosco_emparejamientos p WHERE p.caducaEn < :antesDe")
    int borrarCaducadosAntesDe(@Param("antesDe") Instant antesDe);
}
