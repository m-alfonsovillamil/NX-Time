package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entrada de la auditoría inalterable de fichajes (Fase 8 del plan de
 * profesionalización). Tabla "auditoria_fichaje", **append-only**: la
 * aplicación solo tiene permiso de INSERT/SELECT sobre ella a nivel de
 * PostgreSQL (ver V3__audit_trail.sql y el rol "nxtime_app") -- ni un
 * bug futuro en el código Java puede alterar o borrar una fila ya
 * escrita, no es solo una convención de "no hay ningún método update()
 * en el repositorio".
 *
 * Se crea desde {@link com.nxtime.nxtime.audit.TimeEntryAuditListener},
 * nunca directamente desde el servicio: {@link
 * com.nxtime.nxtime.service.impl.TimeEntryServiceImpl} publica un
 * evento con los datos del cambio y es el listener quien calcula el
 * encadenamiento de hashes y persiste la fila, dentro de la misma
 * transacción que el propio fichaje (@TransactionalEventListener,
 * fase BEFORE_COMMIT: si no se puede auditar, no se ficha).
 *
 * valorAnterior/valorNuevo son JSON crudo (una instantánea del
 * TimeEntry antes/después del cambio), mapeados directo a "jsonb" con
 * el soporte nativo de Hibernate 6 (@JdbcTypeCode(SqlTypes.JSON)) --
 * sin librerías adicionales.
 *
 * hash/hashAnterior: encadenamiento tipo blockchain (cada fila guarda
 * el hash SHA-256 de la anterior, además del suyo propio) para poder
 * detectar si alguien manipulase esta tabla directamente en la base de
 * datos, saltándose la aplicación por completo -- un hash roto en
 * cualquier punto de la cadena invalida todo lo que viene después. Ver
 * TimeEntryAuditListener.
 */
@Entity(name = "auditoria_fichaje")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntryAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "registro_id")
    private TimeEntry registro;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User usuario;

    /** Quién ha hecho el cambio: el propio empleado al fichar, o un RRHH/ADMIN al corregir. */
    @ManyToOne
    @JoinColumn(name = "modificado_por_id")
    private User modificadoPor;

    @Enumerated(EnumType.STRING)
    private AuditAction accion;

    @JdbcTypeCode(SqlTypes.JSON)
    private String valorAnterior;

    @JdbcTypeCode(SqlTypes.JSON)
    private String valorNuevo;

    private String motivo;

    private Instant fechaHora;

    private String ip;

    private String hashAnterior;

    private String hash;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TimeEntryAudit other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
