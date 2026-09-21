package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.RefreshToken;
import com.nxtime.nxtime.domain.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Busca por el hash, no por el token (Fase A11).
     *
     * El token en claro no está en la base y no se puede reconstruir desde
     * aquí: quien pregunta calcula el sha256 y busca por él. Ver
     * {@link RefreshToken#getTokenHash()}.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Cierra todas las sesiones de una persona. Lo usa AccessCodeService al
     * fijar una contraseña con un código (ver ADR 014).
     *
     * @return cuántas sesiones estaban abiertas
     */
    @Modifying
    @Query("UPDATE refresh_tokens r SET r.revocado = true, r.revocadoEn = :cuando "
            + "WHERE r.usuario = :usuario AND r.revocado = false")
    int revocarTodasLasDe(@Param("usuario") User usuario, @Param("cuando") Instant cuando);

    /**
     * Cierra una familia entera: la cadena de tokens que arranca en un login.
     *
     * Se llama cuando alguien presenta un token ya rotado, que significa que
     * hay dos clientes usando la misma cadena. No se puede saber cuál es el
     * legítimo, así que caen los dos -- ver {@link RefreshToken}.
     *
     * Solo esa familia: las demás sesiones de esa persona (el móvil, otro
     * navegador) no tienen por qué pagar por esto.
     *
     * @return cuántas sesiones se han cerrado
     */
    @Modifying
    @Query("UPDATE refresh_tokens r SET r.revocado = true, r.revocadoEn = :cuando "
            + "WHERE r.familia = :familia AND r.revocado = false")
    int revocarLaFamilia(@Param("familia") UUID familia, @Param("cuando") Instant cuando);
}
