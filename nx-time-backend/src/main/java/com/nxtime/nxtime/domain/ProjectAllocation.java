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
 * Cuántos segundos de una jornada van a un proyecto (V23, ADR 017).
 *
 * Es lo que leen los informes de horas por proyecto. La suma de las de una
 * jornada cerrada es su neto; lo mantiene {@code ProjectAllocationService}.
 */
@Entity(name = "imputaciones_proyecto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectAllocation {

    /** De dónde sale el reparto, y por tanto cómo se ajusta si cambia el neto. */
    public enum Origen {
        /** Calculado de los tramos: se recalcula de ellos. */
        TRAMOS,
        /** Repartido a mano (o tras corregir las horas): se reescala en proporción. */
        MANUAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @ManyToOne
    @JoinColumn(name = "registro_id")
    private TimeEntry registro;

    @ManyToOne
    @JoinColumn(name = "proyecto_id")
    private Project proyecto;

    private long segundos;

    @Enumerated(EnumType.STRING)
    private Origen origen;

    @Builder.Default
    private Instant actualizadaEn = Instant.now();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProjectAllocation other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
