package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Derecho anual de vacaciones de un empleado (Fase 9). Tabla
 * "saldo_vacaciones".
 *
 * Guarda SOLO los días a los que el empleado tiene derecho ese año; los
 * días consumidos NO se guardan, se calculan sobre sus peticiones
 * APROBADAS (ver {@link
 * com.nxtime.nxtime.service.VacationBalanceService}). Un contador de
 * consumidos habría que mantenerlo sincronizado en cada aprobación,
 * rechazo o corrección, y cualquier fallo dejaría el saldo mintiendo en
 * silencio; derivarlo tiene una única fuente de verdad.
 */
@Entity(name = "saldo_vacaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    private int anio;

    private int diasTotales;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VacationBalance other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
