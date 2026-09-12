package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Un código de un solo uso para elegir contraseña (ver ADR 014 y
 * V16__codigos_de_acceso.sql).
 *
 * Del código en claro no queda rastro: solo {@link #codigoHash}. El valor
 * de verdad existe una vez, en el correo que lo lleva.
 */
@Entity(name = "codigos_acceso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessCode {

    /**
     * Intentos fallidos que anulan un código. Con seis dígitos, cinco
     * intentos dan una probabilidad de acertar de 1 entre 200.000.
     */
    public static final int MAXIMO_INTENTOS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @Enumerated(EnumType.STRING)
    private AccessCodeType tipo;

    private String codigoHash;

    private Instant creadoEn;

    private Instant expiraEn;

    @Builder.Default
    private int intentosFallidos = 0;

    private Instant usadoEn;

    private Instant anuladoEn;

    /** Si todavía se puede usar: ni usado, ni anulado, ni caducado, ni agotado. */
    public boolean estaVigente(Instant ahora) {
        return usadoEn == null
                && anuladoEn == null
                && ahora.isBefore(expiraEn)
                && intentosFallidos < MAXIMO_INTENTOS;
    }
}
