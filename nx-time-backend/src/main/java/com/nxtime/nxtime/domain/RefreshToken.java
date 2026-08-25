package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Refresh token persistido y revocable (Fase 4). Es una cadena opaca
 * (UUID aleatorio), no un JWT: al vivir en base de datos, revocarlo es
 * un simple UPDATE, sin tener que esperar a que expire por su cuenta
 * -- justo lo que un JWT firmado no permite sin mantener una lista de
 * revocación aparte.
 *
 * Un usuario puede tener varios refresh tokens vivos a la vez (uno por
 * dispositivo/sesión); cada uno se revoca de forma independiente al
 * hacer logout.
 */
@Entity(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String token;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    private Instant expiraEn;

    @Builder.Default
    private boolean revocado = false;

    private Instant creadoEn;

    public boolean estaVivo() {
        return !revocado && expiraEn.isAfter(Instant.now());
    }
}
