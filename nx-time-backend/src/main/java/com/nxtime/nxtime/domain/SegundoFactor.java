package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * El secreto TOTP de una persona (V47, ADR 051). Una fila como mucho por
 * persona.
 *
 * Sin {@code confirmadoEn} es una activación a medias: el secreto ya se ha
 * enseñado, pero hasta que la persona escriba un código bueno no se le pide al
 * entrar. Cuándo está activo de verdad lo dice también
 * {@code usuarios.segundo_factor_desde}, que es lo que lee cada petición.
 *
 * {@code ultimoPaso} no se escribe con un {@code save}: lo mueve una sentencia
 * que solo lo sube (ver {@code SegundoFactorRepository#usarPaso}), para que dos
 * peticiones a la vez con el mismo código no valgan las dos.
 */
@Entity(name = "segundo_factor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SegundoFactor {

    @Id
    @Column(name = "usuario_id")
    private long usuarioId;

    /** Ver {@code SecretosTotp}: nunca en claro. */
    @Column(name = "secreto_cifrado", nullable = false)
    private String secretoCifrado;

    @Builder.Default
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    @Column(name = "confirmado_en")
    private Instant confirmadoEn;

    @Column(name = "ultimo_paso", insertable = false, updatable = false)
    private Long ultimoPaso;

    public boolean confirmado() {
        return confirmadoEn != null;
    }
}
