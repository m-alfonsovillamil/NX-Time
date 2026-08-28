package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fichaje (jornada de trabajo, con sus pausas). Tabla "registros".
 *
 * Cambios de la Fase 3 (PostgreSQL):
 *  - horaEntrada/horaSalida/inicioPausaActual pasan de LocalDateTime a
 *    Instant, y la columna a TIMESTAMPTZ: un fichaje es un instante
 *    concreto en el tiempo, no una fecha-hora "ingenua" sin zona (ver
 *    auditoría, "lo que falta"). LocalDateTime era ambiguo en los
 *    cambios de hora (octubre/marzo); Instant no lo es. La
 *    presentación en hora española se hace en el cliente (ver
 *    TimeEntryMapper y la app Android).
 *  - IDs con GenerationType.IDENTITY en vez de TABLE (ver Company.java).
 *  - Se añade "empresa" (denormalizado desde usuario.empresa) y
 *    "version" (bloqueo optimista) -- ver el esquema V1__initial_schema.sql.
 *
 * Cambios ya hechos en la Fase 2 (se mantienen):
 *  - Sin campo "pausas" (vestigio muerto, ver auditoría).
 *  - segundosPausaAcumulados en vez de minutosPausaAcumulados: no
 *    trunca por pausa individual, acumula en segundos y deriva los
 *    minutos una sola vez sobre el total real (ver TimeEntryMapper).
 *
 * Cambios de la Fase 8 (auditoría inalterable de fichajes):
 *  - "anulado"/"registroOriginal": una corrección (RRHH/ADMIN, ver
 *    TimeEntryServiceImpl.correctTimeEntry) NUNCA sobrescribe
 *    horaEntrada/horaSalida en la fila original -- crea una fila nueva
 *    con los valores correctos y "registroOriginal" apuntando a la que
 *    corrige, y marca la original "anulado = true". El historial
 *    (findHistoryByUsuario/findTeamHistory) deja de mostrar las filas
 *    anuladas; TimeEntryAudit conserva la traza completa de ambas.
 */
@Entity(name = "registros")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private Instant horaEntrada;

    private Instant horaSalida;

    private boolean enPausa;

    private Instant inicioPausaActual;

    @Builder.Default
    private long segundosPausaAcumulados = 0;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Company empresa;

    @Builder.Default
    private boolean anulado = false;

    @ManyToOne
    @JoinColumn(name = "registro_original_id")
    private TimeEntry registroOriginal;

    @Version
    private long version;

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
