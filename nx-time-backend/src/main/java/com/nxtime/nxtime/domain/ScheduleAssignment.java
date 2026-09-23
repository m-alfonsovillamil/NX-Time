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
 * Qué plantilla de horario tiene una persona, y <b>desde cuándo hasta
 * cuándo</b> (Fase B1). Tabla "asignaciones_horario".
 *
 * Calcada de {@link ProjectAssignment}, y por el mismo motivo (ADR 009): el
 * horario teórico de un día es el que la persona tenía ESE día. Un cuadrante
 * que cambia en marzo no puede reescribir febrero, que ya está informado y
 * auditado.
 *
 * <b>Un solo cuadrante por persona y día</b>, y lo impone la base
 * ({@code ex_asignaciones_horario_sin_solape}), no este código.
 */
@Entity(name = "asignaciones_horario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /** Denormalizado, como en todas las tablas por empresa (ADR 006). */
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @ManyToOne
    @JoinColumn(name = "plantilla_id")
    private ScheduleTemplate plantilla;

    private LocalDate fechaInicio;

    /** Null = sigue vigente, sin fecha de fin prevista. */
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
        if (!(o instanceof ScheduleAssignment other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
