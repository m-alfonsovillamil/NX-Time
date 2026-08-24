package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.TableGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Empresa (tenant). Tabla "empresas" -- nombre de tabla y estrategia de
 * generación de IDs sin tocar en esta fase: es persistencia, se rediseña
 * en la Fase 3 (PostgreSQL + Flyway + IDENTITY).
 */
@Entity(name = "empresas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @TableGenerator(
            name = "empresa_gen",
            table = "id_generator",
            pkColumnName = "gen_name",
            valueColumnName = "gen_val",
            allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "empresa_gen")
    private long id;

    private String nombre;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Company other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
