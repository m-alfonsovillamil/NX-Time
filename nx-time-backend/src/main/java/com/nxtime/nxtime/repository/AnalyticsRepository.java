package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Las consultas de la analítica de absentismo y puntualidad (Fase B4).
 *
 * <b>Todas nativas</b>, como las del panel: el rango natural es un año de una
 * empresa entera, y contar eso trayéndose entidades es justo lo que las fases
 * A5 y A6 quitaron. Además {@code generate_series}, {@code FILTER},
 * {@code GROUPING SETS} y {@code percentile_cont} no existen en JPQL.
 *
 * Una consulta por pregunta, con su proyección, y ninguna montada a trozos:
 * ni un {@code switch} que concatene SQL según lo que se pida. Lo que cambia
 * entre una petición y otra son los parámetros, nunca el texto.
 *
 * Los días son días de España ({@code AT TIME ZONE 'Europe/Madrid'}) y una
 * jornada cuenta el día en que EMPIEZA, igual que en el resto del sistema.
 *
 * Todas las que reciben {@code usuarioIds} fallan con una lista vacía
 * ({@code IN ()} no es SQL): el servicio no las llama si no hay nadie.
 */
public interface AnalyticsRepository extends Repository<User, Long> {

    /**
     * Quién entra en la cuenta: las personas de la empresa (y del
     * departamento, si se pide) que habían empezado a fichar antes de que
     * acabe el periodo y no se habían ido antes de que empezara.
     *
     * <b>"Empezar a fichar" hace de fecha de alta</b>, porque el modelo no
     * tiene otra (ver ADR 026). Sin esto, alguien contratado en septiembre
     * saldría con ocho meses de absentismo en el informe del año.
     *
     * El {@code CAST} del departamento no es decoración: con un parámetro
     * nulo sin tipo, PostgreSQL no sabe de qué tipo es y la consulta falla
     * ("could not determine data type of parameter").
     */
    @Query(value = """
            SELECT u.id AS usuarioId,
                   TRIM(u.nombre || ' ' || COALESCE(u.apellidos, '')) AS nombre,
                   d.id AS departamentoId,
                   d.nombre AS departamento,
                   MIN((r.hora_entrada AT TIME ZONE 'Europe/Madrid')::date) AS primerDia,
                   (u.fecha_baja AT TIME ZONE 'Europe/Madrid')::date AS diaDeBaja
            FROM usuarios u
            JOIN registros r ON r.usuario_id = u.id AND r.anulado = false
            LEFT JOIN departamentos d ON d.id = u.departamento_id
            WHERE u.empresa_id = :empresaId
              AND (CAST(:departamentoId AS BIGINT) IS NULL
                   OR u.departamento_id = CAST(:departamentoId AS BIGINT))
              AND (u.fecha_baja IS NULL
                   OR (u.fecha_baja AT TIME ZONE 'Europe/Madrid')::date >= :desde)
            GROUP BY u.id, u.nombre, u.apellidos, d.id, d.nombre, u.fecha_baja
            HAVING MIN((r.hora_entrada AT TIME ZONE 'Europe/Madrid')::date) <= :hasta
            ORDER BY nombre, u.id
            """, nativeQuery = true)
    List<PersonaProjection> personas(
            @Param("empresaId") long empresaId,
            @Param("departamentoId") Long departamentoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /** Los días en que cada persona tiene alguna jornada no anulada, abierta o cerrada. */
    @Query(value = """
            SELECT DISTINCT r.usuario_id AS usuarioId,
                   (r.hora_entrada AT TIME ZONE 'Europe/Madrid')::date AS dia
            FROM registros r
            WHERE r.usuario_id IN (:usuarioIds)
              AND r.anulado = false
              AND r.hora_entrada >= :desde
              AND r.hora_entrada < :hasta
            """, nativeQuery = true)
    List<UsuarioDiaProjection> diasConJornada(
            @Param("usuarioIds") Collection<Long> usuarioIds,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /**
     * Las ausencias APROBADAS, día a día y recortadas al rango. Una pendiente
     * no cuenta, igual que en {@code NonWorkingDayService}: puede acabar
     * rechazada.
     *
     * Si dos ausencias aprobadas se pisan el mismo día (no debería pasar,
     * pero nada lo impide en la base), sale una sola: la más antigua. Contar
     * el día dos veces inflaría el absentismo.
     */
    @Query(value = """
            SELECT DISTINCT ON (a.usuario_id, g.dia)
                   a.usuario_id AS usuarioId,
                   g.dia::date AS dia,
                   a.tipo AS tipo
            FROM peticiones_ausencia a
            CROSS JOIN LATERAL generate_series(
                    GREATEST(a.fecha_inicio, :desde),
                    LEAST(a.fecha_fin, :hasta),
                    INTERVAL '1 day') AS g(dia)
            WHERE a.usuario_id IN (:usuarioIds)
              AND a.estado = 'APROBADA'
              AND a.fecha_inicio <= :hasta
              AND a.fecha_fin >= :desde
            ORDER BY a.usuario_id, g.dia, a.id
            """, nativeQuery = true)
    List<AusenciaDiaProjection> diasDeAusencia(
            @Param("usuarioIds") Collection<Long> usuarioIds,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Las ausencias de cuadrante (Fase B2) cuya explicación se ACEPTÓ: el día
     * se faltó, pero con motivo. Las pendientes, explicadas sin decidir o
     * rechazadas siguen siendo días sin fichaje ni ausencia.
     */
    @Query(value = """
            SELECT i.usuario_id AS usuarioId, i.fecha AS dia
            FROM incidencias_cuadrante i
            WHERE i.usuario_id IN (:usuarioIds)
              AND i.tipo = 'AUSENCIA'
              AND i.estado = 'ACEPTADA'
              AND i.fecha BETWEEN :desde AND :hasta
            """, nativeQuery = true)
    List<UsuarioDiaProjection> ausenciasAceptadas(
            @Param("usuarioIds") Collection<Long> usuarioIds,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Los retrasos, agregados a los tres niveles en UNA pasada: la empresa
     * entera, cada departamento y cada persona ({@code GROUPING SETS}).
     *
     * Sobre {@code incidencias_cuadrante}, que ya está agregada por día: la
     * consulta es barata aunque el periodo sea un año. Cuenta los retrasos en
     * cualquier estado: uno explicado y aceptado sigue siendo un retraso, solo
     * que con motivo. Una incidencia solo existe por encima de la tolerancia
     * (10 minutos), así que aquí no hay retrasos "pequeños".
     *
     * {@code nivel} dice a qué agregado pertenece cada fila; en las de
     * departamento, un {@code departamentoId} nulo es "sin departamento", no
     * el total.
     */
    @Query(value = """
            SELECT CASE WHEN GROUPING(i.usuario_id) = 0 THEN 'EMPLEADO'
                        WHEN GROUPING(u.departamento_id) = 0 THEN 'DEPARTAMENTO'
                        ELSE 'EMPRESA' END AS nivel,
                   u.departamento_id AS departamentoId,
                   i.usuario_id AS usuarioId,
                   COUNT(*) AS retrasos,
                   AVG(i.minutos) AS media,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY i.minutos) AS mediana,
                   COUNT(*) FILTER (WHERE i.minutos <= 30) AS hasta30,
                   COUNT(*) FILTER (WHERE i.minutos > 30) AS masDe30
            FROM incidencias_cuadrante i
            JOIN usuarios u ON u.id = i.usuario_id
            WHERE i.usuario_id IN (:usuarioIds)
              AND i.tipo = 'RETRASO'
              AND i.fecha BETWEEN :desde AND :hasta
            GROUP BY GROUPING SETS ((), (u.departamento_id), (i.usuario_id))
            """, nativeQuery = true)
    List<RetrasosProjection> retrasos(
            @Param("usuarioIds") Collection<Long> usuarioIds,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * El contexto del resumen: cuántas jornadas cerradas hubo, cuántas cerró
     * el sistema por falta de salida, y cuánto se trabajó en total.
     */
    @Query(value = """
            SELECT COUNT(*) AS jornadas,
                   COUNT(*) FILTER (WHERE r.jornada_incompleta) AS incompletas,
                   COALESCE(SUM(EXTRACT(EPOCH FROM (r.hora_salida - r.hora_entrada))
                                - r.segundos_pausa_acumulados), 0) AS segundos
            FROM registros r
            WHERE r.usuario_id IN (:usuarioIds)
              AND r.anulado = false
              AND r.hora_salida IS NOT NULL
              AND r.hora_entrada >= :desde
              AND r.hora_entrada < :hasta
            """, nativeQuery = true)
    JornadasProjection jornadas(
            @Param("usuarioIds") Collection<Long> usuarioIds,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    interface PersonaProjection {
        Long getUsuarioId();

        String getNombre();

        Long getDepartamentoId();

        String getDepartamento();

        LocalDate getPrimerDia();

        /** Null si sigue en la empresa. */
        LocalDate getDiaDeBaja();
    }

    interface UsuarioDiaProjection {
        Long getUsuarioId();

        LocalDate getDia();
    }

    interface AusenciaDiaProjection {
        Long getUsuarioId();

        LocalDate getDia();

        String getTipo();
    }

    interface RetrasosProjection {
        String getNivel();

        Long getDepartamentoId();

        Long getUsuarioId();

        Long getRetrasos();

        Double getMedia();

        Double getMediana();

        Long getHasta30();

        Long getMasDe30();
    }

    interface JornadasProjection {
        Long getJornadas();

        Long getIncompletas();

        Double getSegundos();
    }
}
