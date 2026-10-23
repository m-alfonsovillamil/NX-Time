package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.CodigoDeRecuperacion;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Los códigos de recuperación del segundo factor (V47, ADR 051). Solo se
 * insertan, se gastan y se cuentan, y cada una de esas cosas es una sentencia.
 */
public interface CodigoDeRecuperacionRepository extends Repository<CodigoDeRecuperacion, Long> {

    @Modifying
    @Query(value = "INSERT INTO codigos_recuperacion (usuario_id, hash) VALUES (:usuarioId, :hash)", nativeQuery = true)
    void guardar(@Param("usuarioId") long usuarioId, @Param("hash") String hash);

    /**
     * Gasta un código si existe y no se ha usado. 1 si se ha gastado ahora; 0
     * si no existe, ya se usó, o se lo ha llevado otra petición a la vez.
     */
    @Modifying
    @Query(value = "UPDATE codigos_recuperacion SET usado_en = :ahora "
            + "WHERE usuario_id = :usuarioId AND hash = :hash AND usado_en IS NULL", nativeQuery = true)
    int gastar(@Param("usuarioId") long usuarioId, @Param("hash") String hash, @Param("ahora") Instant ahora);

    @Query(value = "SELECT count(*) FROM codigos_recuperacion WHERE usuario_id = :usuarioId AND usado_en IS NULL",
            nativeQuery = true)
    int quedan(@Param("usuarioId") long usuarioId);

    @Modifying
    @Query(value = "DELETE FROM codigos_recuperacion WHERE usuario_id = :usuarioId", nativeQuery = true)
    int borrarLosDe(@Param("usuarioId") long usuarioId);
}
