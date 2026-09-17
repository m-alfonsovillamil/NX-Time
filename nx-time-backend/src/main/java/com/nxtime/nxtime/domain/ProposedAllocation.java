package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una línea del reparto por proyecto que propone una solicitud de corrección
 * (V25, ADR 017).
 *
 * Existe porque repartir las horas de una semana pasada no lo aplica la
 * persona: lo aprueba un gestor, y hasta entonces el reparto tiene que estar
 * guardado en algún sitio.
 */
@Entity(name = "repartos_propuestos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProposedAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "solicitud_id")
    private CorrectionRequest solicitud;

    @ManyToOne
    @JoinColumn(name = "proyecto_id")
    private Project proyecto;

    private long segundos;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProposedAllocation other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
