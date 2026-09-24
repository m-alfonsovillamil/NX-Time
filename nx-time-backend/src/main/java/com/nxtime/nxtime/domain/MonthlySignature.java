package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.YearMonth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * La firma de una persona sobre su registro de un mes (Fase B3, ADR 025).
 *
 * Firma de aceptación, no eIDAS: guarda el SHA-256 del resumen canónico del
 * mes ({@code HuellaDelMes}) y cuándo se firmó. Una corrección posterior del
 * mes no se bloquea: la deja {@link MonthlySignatureStatus#INVALIDADA}, y la
 * fila se queda como histórico.
 */
@Entity(name = "firmas_mensuales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlySignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    private int anio;

    private int mes;

    private String hash;

    private short versionHuella;

    private int jornadas;

    private long segundosNetos;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MonthlySignatureStatus estado = MonthlySignatureStatus.VIGENTE;

    private Instant firmadaEn;

    private String ip;

    private Instant invalidadaEn;

    private String motivoInvalidacion;

    @ManyToOne
    @JoinColumn(name = "visada_por_id")
    private User visadaPor;

    private Instant visadaEn;

    @Version
    private long version;

    public YearMonth periodo() {
        return YearMonth.of(anio, mes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MonthlySignature other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
