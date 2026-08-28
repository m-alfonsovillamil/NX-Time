package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Día festivo del calendario laboral (Fase 9). Tabla "festivos".
 *
 * {@code empresa == null} significa festivo NACIONAL, aplicable a todas
 * las empresas; con valor, es un festivo propio de esa empresa
 * (autonómico, local o de convenio). Así una única tabla cubre los dos
 * casos sin duplicar las fechas nacionales por cada empresa.
 *
 * "fecha" es LocalDate, no Instant, por la misma razón que
 * AbsenceRequest.fechaInicio: un festivo es un día de calendario, no un
 * instante concreto.
 */
@Entity(name = "festivos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    private LocalDate fecha;

    private String descripcion;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Holiday other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
