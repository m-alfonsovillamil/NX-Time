package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Lo que hace o consulta el operador desde el panel de plataforma (V43, ADR
 * 046). Solo se inserta: la tabla tiene un trigger que impide cambiarla o
 * borrarla, y aquí no hay setters.
 *
 * Sin relaciones a propósito: una empresa rechazada se borra entera y su
 * historial tiene que quedar, con el nombre que tenía.
 */
@Entity(name = "acciones_de_plataforma")
@Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformAction {

    public enum Tipo {
        /** Abrir el detalle de una empresa, que enseña los correos de sus ADMIN. */
        CONSULTAR,
        APROBAR,
        RECHAZAR,
        SUSPENDER,
        REACTIVAR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(nullable = false)
    private Instant en = Instant.now();

    @Column(name = "operador_id", nullable = false)
    private long operadorId;

    @Column(name = "operador_email", nullable = false)
    private String operadorEmail;

    @Column(name = "empresa_id", nullable = false)
    private long empresaId;

    @Column(name = "empresa_nombre", nullable = false)
    private String empresaNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Tipo accion;

    @Column(length = 1000)
    private String motivo;
}
