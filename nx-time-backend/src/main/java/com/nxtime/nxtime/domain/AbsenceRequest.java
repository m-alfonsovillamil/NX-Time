package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Solicitud de ausencia (vacaciones, baja médica...). Tabla
 * "peticiones_ausencia".
 */
@Entity(name = "peticiones_ausencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbsenceRequest {

    @Id
    @TableGenerator(
            name = "peticion_gen",
            table = "id_generator",
            pkColumnName = "gen_name",
            valueColumnName = "gen_val",
            allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "peticion_gen")
    private long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    private AbsenceType tipo;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AbsenceStatus estado = AbsenceStatus.PENDIENTE;

    private String motivo;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbsenceRequest other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
