package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.DatoConsultado;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.DataAccessResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quién ha consultado datos personales de otra persona (plan J, fase J5; ADR
 * 047; RGPD, arts. 5.2 y 32). Lo propio no se anota.
 *
 * <b>Se anota en una transacción propia</b> ({@code REQUIRES_NEW}): casi todo
 * lo que se anota se lee dentro de una transacción de solo lectura, donde
 * PostgreSQL no deja insertar. Y así queda escrito aunque lo que viene
 * después falle: se anota el intento de mirar, que es lo que importa.
 *
 * Si la anotación falla, falla la consulta. Es a propósito: un acceso que no
 * queda registrado es justo lo que esta tabla tiene que impedir.
 */
@Component
public class RegistroDeAccesos {

    /** Lo que se enseña de una vez. El plazo de conservación (2 años) lo pone {@link ConservacionDeDatos}. */
    static final int MAXIMO = 500;

    private final JdbcTemplate jdbc;

    public RegistroDeAccesos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param sobreId de quién son los datos; null si son de muchos o si
     *     anotarlo rompería una confidencialidad (una denuncia)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void anotar(User quien, DatoConsultado que, Long sobreId, String detalle) {
        if (sobreId != null && sobreId == quien.getId()) {
            return;
        }
        jdbc.update("""
                INSERT INTO accesos_a_datos (empresa_id, quien_id, que, sobre_id, detalle)
                VALUES (?, ?, ?, ?, ?)
                """, quien.getEmpresa().getId(), quien.getId(), que.name(), sobreId,
                detalle == null ? null : detalle.substring(0, Math.min(detalle.length(), 160)));
    }

    /** Lo de su empresa, lo último primero; solo lo de una persona si se da {@code sobreId}. */
    @Transactional(readOnly = true)
    public List<DataAccessResponse> deLaEmpresa(User actor, Long sobreId) {
        return jdbc.query("""
                SELECT a.en, a.quien_id, trim(q.nombre || ' ' || coalesce(q.apellidos, '')) AS quien,
                       a.que, a.sobre_id,
                       CASE WHEN s.id IS NULL THEN NULL ELSE trim(s.nombre || ' ' || coalesce(s.apellidos, '')) END AS sobre,
                       a.detalle
                  FROM accesos_a_datos a
                  LEFT JOIN usuarios q ON q.id = a.quien_id
                  LEFT JOIN usuarios s ON s.id = a.sobre_id
                 WHERE a.empresa_id = ? AND (CAST(? AS BIGINT) IS NULL OR a.sobre_id = ?)
                 ORDER BY a.en DESC, a.id DESC
                 LIMIT ?
                """, (fila, n) -> new DataAccessResponse(
                        fila.getTimestamp("en").toInstant(),
                        fila.getLong("quien_id"),
                        fila.getString("quien"),
                        fila.getString("que"),
                        (Long) fila.getObject("sobre_id", Long.class),
                        fila.getString("sobre"),
                        fila.getString("detalle")),
                actor.getEmpresa().getId(), sobreId, sobreId, MAXIMO);
    }

    /**
     * Para {@link ConservacionDeDatos}: lo que ya ha pasado su plazo de dos
     * años. Con el mismo {@code now() - INTERVAL '2 years'} que el trigger
     * de V44, y no con un límite calculado en Java: con un límite de Java,
     * una fila en el borde (un cambio de hora, un año bisiesto) haría saltar
     * el trigger y el paso fallaría entero cada noche.
     */
    int borrarLosCaducados() {
        return jdbc.update("DELETE FROM accesos_a_datos WHERE en < now() - INTERVAL '2 years'");
    }
}
