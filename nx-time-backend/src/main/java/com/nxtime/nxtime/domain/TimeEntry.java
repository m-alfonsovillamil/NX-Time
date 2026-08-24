package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fichaje (jornada de trabajo, con sus pausas). Tabla "registros".
 *
 * El campo "pausas" no se usa en ningún sitio (vestigio de un diseño
 * anterior, ver auditoría) -- se mantiene en esta fase por fidelidad de
 * migración; se elimina en la Fase 2 junto con el resto de la limpieza
 * de contrato.
 */
@Entity(name = "registros")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntry {

    @Id
    @TableGenerator(
            name = "registros_gen",
            table = "id_generator",
            pkColumnName = "gen_name",
            valueColumnName = "gen_val",
            allocationSize = 1
    )
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "registros_gen")
    private long id;

    private LocalDateTime horaEntrada;

    private LocalDateTime horaSalida;

    private String pausas;

    private boolean enPausa;

    private LocalDateTime inicioPausaActual;

    @Builder.Default
    private long minutosPausaAcumulados = 0;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TimeEntry other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
