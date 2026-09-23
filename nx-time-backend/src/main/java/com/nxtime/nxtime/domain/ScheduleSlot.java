package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.DayOfWeek;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un tramo de trabajo de una plantilla, un día de la semana. Tabla
 * "tramos_plantilla_horario".
 *
 * <b>Minutos desde medianoche, no {@code LocalTime}.</b> Un turno de 22:00 a
 * 06:00 es {@code inicio = 1320, fin = 1800}: una sola fila, con el fin mayor
 * que el inicio. Con horas de reloj el fin sería menor que el inicio, y ni el
 * CHECK ni el solape de V31 se podrían escribir. El tramo pertenece al día en
 * que <b>empieza</b>.
 */
@Entity(name = "tramos_plantilla_horario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "plantilla_id")
    private ScheduleTemplate plantilla;

    /** ISO: 1 = lunes, 7 = domingo. El mismo número que {@link DayOfWeek#getValue()}. */
    private short diaSemana;

    /** Minutos desde la medianoche del día en que empieza, en [0, 1440). */
    private int inicio;

    /** Mayor que {@code inicio}; puede pasar de 1440 si cruza la medianoche. */
    private int fin;

    public DayOfWeek dia() {
        return DayOfWeek.of(diaSemana);
    }

    public int minutos() {
        return fin - inicio;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScheduleSlot other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
