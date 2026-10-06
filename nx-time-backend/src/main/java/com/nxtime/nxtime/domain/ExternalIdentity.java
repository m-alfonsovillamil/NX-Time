package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
 * Una cuenta de Google o de Microsoft con la que una persona entra (ADR 036).
 * Tabla {@code identidades_externas} (V39).
 *
 * Sin {@code @Version}: la fila se crea al vincular, se le apunta el último
 * acceso y se borra al desvincular. Nadie la edita.
 */
@Entity(name = "identidades_externas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @Enumerated(EnumType.STRING)
    private SsoProvider proveedor;

    /** El {@code sub} del ID token: el identificador estable de la cuenta en el proveedor. */
    private String sujeto;

    /** El correo de esa cuenta al vincularla. Solo para enseñarlo. */
    private String correo;

    @Builder.Default
    private Instant vinculadaEn = Instant.now();

    private Instant ultimoAcceso;
}
