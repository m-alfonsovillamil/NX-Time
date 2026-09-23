package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Un día concreto que se sale de la plantilla (Fase B1). Tabla
 * "excepciones_horario".
 *
 * Manda sobre la plantilla ese día. Ver {@link ScheduleExceptionType}.
 *
 * <b>Los festivos y las ausencias aprobadas no van aquí</b>: ya los resuelve
 * {@code NonWorkingDayService}, y copiarlos sería tener dos verdades sobre qué
 * días no se trabaja.
 */
@Entity(name = "excepciones_horario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    private ScheduleExceptionType tipo;

    /** Null en un LIBRE. Minutos desde medianoche, como en {@link ScheduleSlot}. */
    private Integer inicio;

    private Integer fin;

    private String motivo;

    /** Quién la puso. Null si esa cuenta se anonimizó después. */
    @ManyToOne
    @JoinColumn(name = "creada_por_id")
    private User creadaPor;

    public int minutos() {
        return tipo == ScheduleExceptionType.TRAMO ? fin - inicio : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScheduleException other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
