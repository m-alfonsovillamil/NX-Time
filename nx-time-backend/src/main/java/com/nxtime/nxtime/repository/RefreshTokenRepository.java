package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.RefreshToken;
import com.nxtime.nxtime.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    /**
     * Cierra todas las sesiones de una persona. Lo usa AccessCodeService al
     * fijar una contraseña con un código (ver ADR 014).
     *
     * @return cuántas sesiones estaban abiertas
     */
    @Modifying
    @Query("UPDATE refresh_tokens r SET r.revocado = true WHERE r.usuario = :usuario AND r.revocado = false")
    int revocarTodasLasDe(@Param("usuario") User usuario);
}
