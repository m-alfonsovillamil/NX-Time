package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Proyecto al que se imputan horas (Fase D). Tabla "proyectos".
 *
 * Es de una empresa, como los departamentos: dos empresas pueden tener
 * ambas un "MIGRACION" sin que sea el mismo.
 *
 * Las fechas son {@link LocalDate} y no {@code Instant} por la misma
 * razón que en {@link AbsenceRequest}: "el proyecto empieza el 1 de
 * abril" es una fecha de calendario, no un instante concreto.
 *
 * <b>{@code activo} y {@code fechaFin} no son lo mismo</b>, y por eso
 * están los dos: un proyecto puede terminar en su fecha prevista o
 * cancelarse antes de tiempo. Colapsarlos obligaría a inventarse una
 * fecha de fin para algo que se paró sin fecha.
 */
@Entity(name = "proyectos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    /**
     * Lo que se teclea y lo que se reconoce en un informe ("NX-2026-04").
     * Único dentro de la empresa.
     */
    private String codigo;

    private String nombre;

    private String descripcion;

    private LocalDate fechaInicio;

    /** Null mientras no tenga fecha de fin prevista. */
    private LocalDate fechaFin;

    @Builder.Default
    private boolean activo = true;

    @Version
    private long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Project other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
