package com.nxtime.nxtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import java.time.ZoneId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Empresa (tenant). Tabla "empresas".
 *
 * IDs con GenerationType.IDENTITY (BIGSERIAL de PostgreSQL) desde la
 * Fase 3. En SQLite esto no funcionaba (sqlite-jdbc no implementa
 * getGeneratedKeys()) y el workaround con GenerationType.TABLE
 * obligaba a quitar @Transactional de varios métodos de escritura
 * (ver AuthServiceImpl.registerManager antes de esta fase); con
 * PostgreSQL el problema desaparece de raíz.
 *
 * @Version añade bloqueo optimista: dos escrituras concurrentes sobre
 * la misma fila ya no pueden pisarse en silencio.
 *
 * La zona horaria es de cada empresa (ADR 032): decide a qué día pertenece
 * cada fichaje. Todo cálculo de "qué día es" pasa por {@link #zona()}.
 */
@Entity(name = "empresas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    /** La de todas las empresas antes del ADR 032, y la de las nuevas si no se dice otra. */
    public static final String ZONA_POR_DEFECTO = "Europe/Madrid";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String nombre;

    /** Nombre IANA. Se valida al guardarlo; ver {@link #zona()}. */
    @Column(name = "zona_horaria", nullable = false, length = 64)
    @Builder.Default
    private String zonaHoraria = ZONA_POR_DEFECTO;

    @Version
    private long version;

    /** La zona en la que se cuentan los días de esta empresa. */
    public ZoneId zona() {
        return ZoneId.of(zonaHoraria == null ? ZONA_POR_DEFECTO : zonaHoraria);
    }

    /** La zona de una empresa, o la de por defecto si no hay empresa (solo en tests unitarios). */
    public static ZoneId zonaDe(Company empresa) {
        return empresa != null ? empresa.zona() : ZoneId.of(ZONA_POR_DEFECTO);
    }

    /** Quien crea la empresa sin decir zona (el builder o {@code new}) se queda con la de por defecto. */
    @PrePersist
    void zonaPorDefectoSiFalta() {
        if (zonaHoraria == null) {
            zonaHoraria = ZONA_POR_DEFECTO;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Company other)) {
            return false;
        }
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
