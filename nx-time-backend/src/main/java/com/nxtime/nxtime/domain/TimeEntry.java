package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 *    CorrectionServiceImpl, desde la Fase E) NUNCA sobrescribe
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

    /**
     * Jornada que nunca se cerró y que cerró automáticamente el proceso
     * nocturno (Fase 9, ver IncompleteTimeEntryScheduler), no el
     * empleado. Marca la incidencia para que RRHH la corrija con
     * PATCH /api/v1/fichaje/{id}: las horas de salida que puso el
     * sistema son una convención, no un fichaje real.
     */
    @Builder.Default
    private boolean jornadaIncompleta = false;

    // LAZY (ADR 034): EAGER cargaba la cadena entera de originales con cada
    // fichaje corregido, y casi nadie la lee.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_original_id")
    private TimeEntry registroOriginal;

    /**
     * En qué kiosco se abrió la jornada, o null si se abrió desde la propia
     * sesión (ADR 033). Cada movimiento hecho en un kiosco lo dice además en el
     * motivo de su fila de auditoría.
     */
    // LAZY (ADR 034): los historiales solo enseñan su nombre, y los fichajes
    // que no son de kiosco no cuestan nada.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kiosco_id")
    private Kiosk kiosco;

    @Version
    private long version;

    /**
     * La zona de la empresa del fichaje (ADR 032). Sin empresa -- solo en
     * tests unitarios -- la de la persona, y si tampoco, la de por defecto.
     */
    public ZoneId zona() {
        if (empresa != null) {
            return empresa.zona();
        }
        return usuario != null ? usuario.zona() : ZoneId.of(Company.ZONA_POR_DEFECTO);
    }

    /**
     * El día de la jornada: el de su ENTRADA, en la zona de la empresa. Una
     * jornada cuenta entera en el día en que empieza, termine cuando termine.
     */
    public LocalDate dia() {
        return horaEntrada.atZone(zona()).toLocalDate();
    }

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
