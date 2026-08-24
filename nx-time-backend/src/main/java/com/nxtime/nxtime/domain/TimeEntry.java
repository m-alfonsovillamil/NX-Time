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
 * Cambios de esta fase respecto a la migración de la Fase 1:
 *  - Se elimina el campo "pausas" (vestigio muerto, ver auditoría): no
 *    se leía ni escribía en ningún sitio. Con ddl-auto=update la
 *    columna huérfana se queda en la tabla física sin hacer daño; la
 *    Fase 3 la limpia de verdad vía Flyway.
 *  - El acumulador de pausas pasa de "minutosPausaAcumulados" a
 *    "segundosPausaAcumulados": el original sumaba
 *    Duration.toMinutes() por cada pausa individual, truncando hacia
 *    cero -- varias pausas cortas de menos de un minuto se contaban
 *    como 0 en total aunque sumadas superaran el minuto (ver
 *    auditoría). Ahora se acumula en segundos sin truncar por evento,
 *    y el minutaje que ve el cliente se deriva UNA sola vez sobre el
 *    total real (ver TimeEntryMapper). Como la entidad ya no se
 *    serializa directamente (ver defecto #1, corregido en esta fase),
 *    este cambio de nombre no rompe el contrato JSON.
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

    private boolean enPausa;

    private LocalDateTime inicioPausaActual;

    @Builder.Default
    private long segundosPausaAcumulados = 0;

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
