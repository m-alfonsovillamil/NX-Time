package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un día que no cuadró con el cuadrante (Fase B2). Tabla
 * "incidencias_cuadrante".
 *
 * <b>Se detecta, no se imputa</b>, como las horas extra (ADR 011): una
 * incidencia no descuenta nada. Es la constancia de que algo no cuadró, y el
 * sitio donde queda la explicación.
 *
 * Una por persona, día y tipo ({@code uq_incidencias_usuario_fecha_tipo}): el
 * barrido nocturno pasa varias veces por el mismo día y no puede duplicar.
 */
@Entity(name = "incidencias_cuadrante")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleIncident {

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
    private ScheduleIncidentType tipo;

    /** Minutos de retraso, de adelanto en la salida, o teóricos del día en una ausencia. */
    private int minutos;

    /** La hora teórica contra la que se comparó, en minutos desde la medianoche de {@link #fecha}. */
    private int horaPrevista;

    /** La entrada o la salida real. Null en una ausencia. */
    private Instant horaReal;

    /** El fichaje que la originó. Null en una ausencia, o si se anuló después. */
    @ManyToOne
    @JoinColumn(name = "registro_id")
    private TimeEntry registro;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ScheduleIncidentStatus estado = ScheduleIncidentStatus.PENDIENTE;

    private String justificacion;

    private Instant justificadaEn;

    private String comentarioResolucion;

    @ManyToOne
    @JoinColumn(name = "resuelta_por_id")
    private User resueltaPor;

    private Instant resueltaEn;

    @Builder.Default
    private Instant detectadaEn = Instant.now();

    @Version
    private long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScheduleIncident other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
