package com.nxtime.nxtime.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
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
 */
@Entity(name = "empresas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String nombre;

    @Version
    private long version;

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
