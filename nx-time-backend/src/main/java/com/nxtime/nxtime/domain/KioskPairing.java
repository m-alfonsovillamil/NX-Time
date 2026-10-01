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
 * Un emparejamiento en curso entre una tablet y una empresa (ADR 033). Tabla
 * "kiosco_emparejamientos".
 *
 * La tablet enseña un código corto; el ADMIN lo teclea y con eso se crea el
 * kiosco ({@link #kiosco}). La tablet pregunta con su {@code secreto} hasta que
 * está listo y recoge el token una sola vez ({@link #entregadoEn}). De código y
 * secreto solo se guarda el hash.
 */
@Entity(name = "kiosco_emparejamientos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KioskPairing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String codigoHash;

    private String secretoHash;

    @ManyToOne
    @JoinColumn(name = "kiosco_id")
    private Kiosk kiosco;

    private Instant caducaEn;

    private Instant entregadoEn;

    public boolean caducado(Instant ahora) {
        return !ahora.isBefore(caducaEn);
    }
}
