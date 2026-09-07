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
 * Quién está en qué proyecto y <b>desde cuándo hasta cuándo</b> (Fase D).
 * Tabla "asignaciones_proyecto".
 *
 * La vigencia es el motivo de que esto sea una tabla y no un campo
 * {@code proyecto_id} en {@link User}: las horas de un día van al
 * proyecto que esa persona tenía asignado ESE día. Con un campo, cambiar
 * a alguien de proyecto en abril recolocaría todas sus horas de enero y
 * reescribiría informes ya cerrados.
 *
 * <b>Una persona no puede estar en dos proyectos el mismo día</b>, y eso
 * lo impone la base con {@code ex_asignaciones_sin_solape} (un EXCLUDE
 * sobre {@code daterange}), no este código: comprobarlo en Java sería
 * leer y luego escribir, es decir, una condición de carrera.
 *
 * {@code fechaFin} null significa "sigue asignado", y en la restricción
 * se traduce a un rango abierto por la derecha.
 */
@Entity(name = "asignaciones_proyecto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /**
     * Denormalizado desde usuario/proyecto, igual que en
     * {@link TimeEntry} y {@link AbsenceRequest} desde la Fase 3: el
     * filtro de tenant va en todas las consultas, y alcanzarlo con un
     * JOIN lo convertiría en lo más caro de la más barata (ADR 006).
     */
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @ManyToOne
    @JoinColumn(name = "proyecto_id")
    private Project proyecto;

    private LocalDate fechaInicio;

    /** Null = sigue asignado, sin fecha de salida prevista. */
    private LocalDate fechaFin;

    @Version
    private long version;

    /** Si la asignación cubre ese día de calendario. */
    public boolean vigenteEl(LocalDate dia) {
        return !dia.isBefore(fechaInicio) && (fechaFin == null || !dia.isAfter(fechaFin));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProjectAssignment other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
