package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un código de recuperación del segundo factor (V47, ADR 051): solo su
 * SHA-256. Se escribe y se gasta con sentencias propias (ver
 * {@code CodigoDeRecuperacionRepository}); la entidad existe para que el
 * repositorio tenga tipo.
 */
@Entity(name = "codigos_recuperacion")
@Getter
@NoArgsConstructor
public class CodigoDeRecuperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "usuario_id")
    private long usuarioId;

    private String hash;

    @Column(name = "creado_en")
    private Instant creadoEn;

    @Column(name = "usado_en")
    private Instant usadoEn;
}
