package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un horario semanal reutilizable (Fase B1): «oficina», «turno de mañana»,
 * «intensiva de verano». Tabla "plantillas_horario".
 *
 * Una empresa tiene tres o cuatro, no uno por persona: a cada persona se le
 * <b>asigna</b> una con vigencia ({@link ScheduleAssignment}), y los días que
 * se salen de la norma van como {@link ScheduleException}.
 *
 * Los tramos ({@link ScheduleSlot}) no cuelgan de aquí como colección a
 * propósito. Reemplazarlos con {@code orphanRemoval} parece natural, pero
 * Hibernate vuelca los INSERT antes que los DELETE: los tramos nuevos
 * chocarían con los viejos, todavía sin borrar, contra el EXCLUDE de V31. El
 * servicio los borra con una sentencia DELETE, que se ejecuta en el acto, y
 * luego inserta los nuevos.
 */
@Entity(name = "plantillas_horario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    private String nombre;

    private String descripcion;

    @Version
    private long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScheduleTemplate other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
