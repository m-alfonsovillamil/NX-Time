package com.nxtime.nxtime.service.impl;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lo que se borra solo porque ya no hace falta (plan J, fase J5; RGPD, art.
 * 5.1.e: limitación del plazo de conservación). Los plazos, y por qué, están en
 * {@code docs/cumplimiento/conservacion.md}: si se cambia uno aquí, se cambia
 * allí.
 *
 * Corre dentro de la tarea de las 03:45 ({@code DataDeletionScheduler}), y no
 * en una propia, para no despertar la base a otra hora.
 *
 * Cada paso va en su transacción y un fallo en uno no para los demás: lo que
 * no se haya podido borrar esta noche se borra la siguiente, porque cada paso
 * busca «más viejo que», no «de ayer».
 *
 * Va en SQL explícito, como {@link PersonalDataEraser}: es código que destruye
 * datos y tiene que poder leerse entero.
 */
@Component
public class ConservacionDeDatos {

    private static final Logger log = LoggerFactory.getLogger(ConservacionDeDatos.class);

    static final Duration SESIONES = Duration.ofDays(30);
    static final Duration CODIGOS = Duration.ofDays(90);
    static final Duration AVISOS = Duration.ofDays(365);
    static final Duration DISPOSITIVOS = Duration.ofDays(365);
    static final Duration EJECUCIONES = Duration.ofDays(365);
    static final Duration CANDIDATURAS = Duration.ofDays(365);
    /** El registro de jornada se conserva cuatro años (art. 34.9 ET); después, nadie lo necesita. */
    static final int ANIOS_DEL_REGISTRO = 4;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final PersonalDataEraser borrado;
    private final RegistroDeAccesos accesos;

    public ConservacionDeDatos(JdbcTemplate jdbc, TransactionTemplate transaccion, PersonalDataEraser borrado,
            RegistroDeAccesos accesos) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
        this.borrado = borrado;
        this.accesos = accesos;
    }

    /** Aplica todos los plazos. Devuelve cuántas filas se han ido en cada paso, para el resumen de la tarea. */
    public Map<String, Integer> aplicar(Instant ahora) {
        Map<String, Integer> hecho = new LinkedHashMap<>();

        // Las sesiones caducadas, por familias enteras: un refresh rotado
        // apunta a su sucesor, y borrar uno sin el otro rompería ese enlace.
        paso(hecho, "sesiones", () -> jdbc.update("""
                DELETE FROM refresh_tokens
                 WHERE familia IN (SELECT familia FROM refresh_tokens GROUP BY familia HAVING max(expira_en) < ?)
                """, antes(ahora, SESIONES)));

        paso(hecho, "codigos", () -> jdbc.update(
                "DELETE FROM codigos_acceso WHERE COALESCE(usado_en, anulado_en, expira_en) < ?",
                antes(ahora, CODIGOS)));

        paso(hecho, "avisos", () -> jdbc.update("DELETE FROM avisos WHERE creado_en < ?", antes(ahora, AVISOS)));

        paso(hecho, "dispositivosPush", () -> jdbc.update(
                "DELETE FROM dispositivos_push WHERE visto_en < ?", antes(ahora, DISPOSITIVOS)));

        paso(hecho, "ejecucionesDeTareas", () -> jdbc.update(
                "DELETE FROM ejecuciones_tarea WHERE inicio < ?", antes(ahora, EJECUCIONES)));

        // Las candidaturas resueltas hace más de un año, y después los
        // adjuntos que ya no están vigentes ni los usa ninguna candidatura
        // (CV sustituidos, fotos cambiadas). Su contenido se va en cascada.
        paso(hecho, "candidaturas", () -> jdbc.update(
                "DELETE FROM candidaturas WHERE fecha_resolucion < ?", antes(ahora, CANDIDATURAS)));
        paso(hecho, "adjuntosSinUso", () -> jdbc.update("""
                DELETE FROM adjuntos a
                 WHERE a.vigente = FALSE
                   AND NOT EXISTS (SELECT 1 FROM candidaturas c WHERE c.adjunto_cv_id = a.id)
                """));

        // Dos años, y con el reloj de la base: ver RegistroDeAccesos.
        paso(hecho, "registroDeAccesos", accesos::borrarLosCaducados);

        // Quien lleva de baja más de cuatro años desde su último fichaje (o
        // desde la baja, si nunca fichó): el registro ya no hay que
        // conservarlo, así que se anonimiza aunque nadie lo haya pedido. Antes
        // del plan J solo se hacía a petición (ADR 016).
        hecho.put("personasAnonimizadas", anonimizarLasCaducadas(ahora));

        log.info("Conservación aplicada: {}", hecho);
        return hecho;
    }

    private int anonimizarLasCaducadas(Instant ahora) {
        Timestamp limite = Timestamp.from(ahora.atOffset(ZoneOffset.UTC).minusYears(ANIOS_DEL_REGISTRO).toInstant());
        List<Long> caducadas = jdbc.queryForList("""
                SELECT u.id
                  FROM usuarios u
                 WHERE u.activo = FALSE
                   AND u.fecha_baja IS NOT NULL
                   AND u.email NOT LIKE '%@anonimo.invalid'
                   AND COALESCE((SELECT max(r.hora_entrada) FROM registros r WHERE r.usuario_id = u.id), u.fecha_baja) < ?
                   AND u.fecha_baja < ?
                """, Long.class, limite, limite);
        int hechas = 0;
        for (Long usuarioId : caducadas) {
            try {
                transaccion.executeWithoutResult(estado -> {
                    borrado.purgar(usuarioId);
                    borrado.anonimizar(usuarioId);
                });
                hechas++;
                log.info("Usuario {} anonimizado: cuatro años de baja desde su último fichaje.", usuarioId);
            } catch (DataAccessException e) {
                log.warn("No se ha podido anonimizar al usuario {}: {}", usuarioId, e.getMostSpecificCause().getMessage());
            }
        }
        return hechas;
    }

    private void paso(Map<String, Integer> hecho, String nombre, IntSupplier sentencia) {
        try {
            Integer filas = transaccion.execute(estado -> sentencia.getAsInt());
            hecho.put(nombre, filas == null ? 0 : filas);
        } catch (DataAccessException e) {
            log.warn("Conservación: el paso «{}» ha fallado y se reintentará mañana: {}",
                    nombre, e.getMostSpecificCause().getMessage());
            hecho.put(nombre, -1);
        }
    }

    private static Timestamp antes(Instant ahora, Duration plazo) {
        return Timestamp.from(ahora.minus(plazo));
    }
}
