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
    /** Ley 2/2023, art. 32.3: sin investigación, a los tres meses se suprime el contenido. */
    static final int MESES_SIN_INVESTIGAR = 3;
    /** Ley 2/2023, art. 26.2: el libro-registro, nunca más de diez años. */
    static final int ANIOS_DEL_LIBRO_REGISTRO = 10;

    static final String DESCRIPCION_SUPRIMIDA =
            "[Contenido suprimido: pasaron tres meses sin iniciar una investigación (Ley 2/2023, art. 32.3).]";
    static final String CONCLUSION_SUPRIMIDA =
            "Archivada sin investigación. El contenido se suprimió a los tres meses, como manda la Ley 2/2023 "
                    + "(art. 32.3); queda solo que existió, como evidencia del funcionamiento del canal.";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final PersonalDataEraser borrado;
    private final RegistroDeAccesos accesos;
    private final BajaDeEmpresa bajas;

    public ConservacionDeDatos(JdbcTemplate jdbc, TransactionTemplate transaccion, PersonalDataEraser borrado,
            RegistroDeAccesos accesos, BajaDeEmpresa bajas) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
        this.borrado = borrado;
        this.accesos = accesos;
        this.bajas = bajas;
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

        // El canal de denuncias (ADR 048). A los diez años, el expediente
        // entero (los mensajes, en cascada); va primero para no suprimir antes
        // lo que se va a borrar.
        paso(hecho, "libroRegistro", () -> jdbc.update("DELETE FROM denuncias WHERE creado_en < ?",
                Timestamp.from(ahora.atOffset(ZoneOffset.UTC).minusYears(ANIOS_DEL_LIBRO_REGISTRO).toInstant())));

        // Después, lo que lleva tres meses sin
        // investigar: fuera los mensajes, el relato, quién la puso y su
        // contacto, y se archiva. Queda la fila (fecha, categoría, estado)
        // como evidencia de que el canal funciona, que la ley sí permite.
        paso(hecho, "denunciasSinInvestigar", () -> {
            Timestamp limite = Timestamp.from(ahora.atOffset(ZoneOffset.UTC).minusMonths(MESES_SIN_INVESTIGAR).toInstant());
            jdbc.update("""
                    DELETE FROM denuncia_mensajes
                     WHERE denuncia_id IN (SELECT id FROM denuncias
                                            WHERE investigacion_iniciada_en IS NULL
                                              AND contenido_suprimido_en IS NULL
                                              AND creado_en < ?)
                    """, limite);
            return jdbc.update("""
                    UPDATE denuncias
                       SET descripcion = ?, conclusion = ?,
                           denunciante_id = NULL, contacto_notificaciones = NULL,
                           estado = 'ARCHIVADA', resuelta_en = COALESCE(resuelta_en, now()),
                           contenido_suprimido_en = now()
                     WHERE investigacion_iniciada_en IS NULL
                       AND contenido_suprimido_en IS NULL
                       AND creado_en < ?
                    """, DESCRIPCION_SUPRIMIDA, CONCLUSION_SUPRIMIDA, limite);
        });

        // Quien lleva de baja más de cuatro años desde su último fichaje (o
        // desde la baja, si nunca fichó): el registro ya no hay que
        // conservarlo, así que se anonimiza aunque nadie lo haya pedido. Antes
        // del plan J solo se hacía a petición (ADR 016).
        hecho.put("personasAnonimizadas", anonimizarLasCaducadas(ahora));

        // Las empresas que pidieron la baja hace más de treinta días (ADR 050):
        // enteras, auditoría incluida. Cada una en su transacción (ver BajaDeEmpresa).
        hecho.put("empresasBorradas", bajas.borrarLasVencidas(ahora));

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
