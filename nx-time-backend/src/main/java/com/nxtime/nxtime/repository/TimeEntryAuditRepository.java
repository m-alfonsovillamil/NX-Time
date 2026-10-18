package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.audit.FilaDeLaCadena;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solo lectura + inserción desde el código Java (ver TimeEntryAudit):
 * a propósito no hay ningún método de actualización o borrado aquí, ni
 * falta que haga -- la tabla en sí revoca esos permisos al rol de la
 * aplicación (ver V3__audit_trail.sql).
 */
public interface TimeEntryAuditRepository extends JpaRepository<TimeEntryAudit, Long> {

    /** Última fila insertada, para encadenar su hash con la siguiente (ver TimeEntryAuditListener). */
    Optional<TimeEntryAudit> findTopByOrderByIdDesc();

    /**
     * Serializa a quien vaya a encadenar una fila nueva, hasta el commit.
     *
     * Leer la última fila y escribir la siguiente tienen que ser una sola
     * operación: si dos transacciones leen a la vez, las dos anotan el mismo
     * {@code hashAnterior} y la cadena queda bifurcada -- y entonces
     * {@link com.nxtime.nxtime.audit.VerificadorDeAuditoria} denuncia una
     * manipulación que nadie ha hecho. No hace falta que haya varias
     * instancias: dos hilos de Tomcat en READ COMMITTED bastan.
     *
     * Es un advisory lock y no un {@code SELECT ... FOR UPDATE} porque
     * {@code FOR UPDATE} exige privilegio UPDATE sobre la tabla, y
     * V3__audit_trail.sql se lo revoca a propósito al rol de la aplicación:
     * ahí un FOR UPDATE fallaría con "permission denied". El advisory lock lo
     * puede pedir PUBLIC, se suelta solo al hacer commit o rollback --no hay
     * forma de olvidarse de liberarlo-- y, al vivir en PostgreSQL y no en la
     * JVM, protege también si algún día hay más de una instancia.
     *
     * Desde V46 (ADR 050) el cerrojo es de la cadena de UNA empresa: la forma
     * de dos enteros de {@code pg_advisory_xact_lock}, la clave y la empresa.
     * Dos empresas fichando a la vez ya no se esperan la una a la otra.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(:clave, :empresa)", nativeQuery = true)
    void bloquearCadenaDeEmpresa(@Param("clave") int clave, @Param("empresa") int empresa);

    /** El hash de la última fila de la cadena de una empresa, si ya tiene alguna. */
    @Query(value = "SELECT hash FROM auditoria_fichaje WHERE empresa_id = :empresa AND version_hash >= :version "
            + "ORDER BY id DESC LIMIT 1", nativeQuery = true)
    Optional<String> ultimoHashDeLaCadena(@Param("empresa") long empresa, @Param("version") short version);

    /**
     * Un bloque de la traza a partir de un id, en orden de escritura.
     *
     * Por id y no por fecha: el id es el orden en que se escribieron, que es
     * el orden en que se encadenaron los hashes. Dos filas de la misma
     * milésima se ordenarían al azar por fecha y la cadena parecería rota.
     *
     * Por bloques con {@code WHERE id > :desde} y no con un {@code OFFSET}:
     * un OFFSET creciente obliga a PostgreSQL a descartar cada vez más filas,
     * mientras que esto entra siempre por la clave primaria. Antes de la Fase
     * A4 se traía la tabla entera a memoria de una vez.
     *
     * <b>Lo justo para comprobar la cadena, y ninguna entidad.</b> Hasta
     * octubre de 2026 el bloque eran entidades, y cada movimiento traía detrás
     * su fichaje, la persona, su empresa y su departamento, una consulta por
     * cada uno: recorrer millón y medio de movimientos tardaba 46 minutos
     * (docs/PRUEBA-DE-CARGA.md). Aquí todo sale de la propia fila y de un solo
     * cruce con el fichaje, que es quien dice de qué empresa es.
     *
     * El autor va con {@code left join}: en las acciones automáticas no hay,
     * y un cruce normal se dejaría esas filas fuera sin avisar, que para una
     * cadena es lo mismo que haberlas borrado.
     *
     * <b>{@code @Transactional} a propósito</b>: una consulta declarada aquí
     * no abre transacción por sí sola, y sin una la conexión se queda cogida
     * hasta que termina el método que la rodea, que en el recorrido completo
     * es el recorrido entero. Con esto cada bloque coge una conexión, lee y la
     * suelta. No hace falta que todos los bloques vayan en la misma: la tabla
     * es de solo inserción y cada fila se numera con el cerrojo de la cadena
     * cogido, así que por detrás de una fila ya leída no puede aparecer otra.
     *
     * @param desdeId exclusivo; 0 para empezar por el principio
     */
    @Transactional(readOnly = true)
    @Query("""
            select new com.nxtime.nxtime.audit.FilaDeLaCadena(
                a.id, r.id, a.usuario.id, autor.id, a.accion, a.valorAnterior, a.valorNuevo, a.motivo,
                a.fechaHora, a.hashAnterior, a.hash, a.versionHash, r.empresa.id)
            from auditoria_fichaje a
            join a.registro r
            left join a.modificadoPor autor
            where a.id > :desdeId and a.versionHash < 3
            order by a.id asc
            """)
    List<FilaDeLaCadena> findBloqueDelTramoComun(@Param("desdeId") long desdeId, Pageable bloque);

    /**
     * Lo mismo, de la cadena de UNA empresa (V46, ADR 050): sus filas de la
     * versión 3, en orden de escritura. Entra por {@code ix_auditoria_cadena_de_empresa}.
     */
    @Transactional(readOnly = true)
    @Query("""
            select new com.nxtime.nxtime.audit.FilaDeLaCadena(
                a.id, r.id, a.usuario.id, autor.id, a.accion, a.valorAnterior, a.valorNuevo, a.motivo,
                a.fechaHora, a.hashAnterior, a.hash, a.versionHash, a.empresaId)
            from auditoria_fichaje a
            join a.registro r
            left join a.modificadoPor autor
            where a.empresaId = :empresaId and a.versionHash >= 3 and a.id > :desdeId
            order by a.id asc
            """)
    List<FilaDeLaCadena> findBloqueDeLaCadenaDeEmpresa(
            @Param("empresaId") long empresaId, @Param("desdeId") long desdeId, Pageable bloque);

    /** Las empresas que tienen cadena propia, es decir, alguna fila de la versión 3. */
    @Transactional(readOnly = true)
    @Query(value = """
            SELECT e.id FROM empresas e
             WHERE EXISTS (SELECT 1 FROM auditoria_fichaje a WHERE a.empresa_id = e.id AND a.version_hash >= 3)
             ORDER BY e.id
            """, nativeQuery = true)
    List<Long> empresasConCadena();

    /** Filas de la cadena de una empresa, en total y después de un id. */
    @Query("select count(a) from auditoria_fichaje a where a.empresaId = :empresaId and a.versionHash >= 3 and a.id > :desdeId")
    long contarDeLaCadenaDeEmpresaDesde(@Param("empresaId") long empresaId, @Param("desdeId") long desdeId);

    /** Filas del tramo común de una empresa después de un id. */
    @Query("select count(a) from auditoria_fichaje a where a.registro.empresa.id = :empresaId "
            + "and a.versionHash < 3 and a.id > :desdeId")
    long contarDelTramoComunDeEmpresaDesde(@Param("empresaId") long empresaId, @Param("desdeId") long desdeId);

    /** Filas del tramo común después de un id. */
    @Query("select count(a) from auditoria_fichaje a where a.versionHash < 3 and a.id > :desdeId")
    long contarDelTramoComunDesde(@Param("desdeId") long desdeId);

    /** Cuántas filas quedan por detrás de un punto de control. */
    long countByIdGreaterThan(long id);

    /**
     * ¿Sigue ahí, y con el mismo hash, la fila hasta la que llegó un punto de
     * control? Si no, es que a la traza le han quitado el final o se lo han
     * reescrito después de comprobarlo (ADR 042).
     */
    boolean existsByIdAndHash(long id, String hash);

    /*
     * Lo mismo, pero solo lo de UNA empresa: es lo que se le enseña a quien
     * pregunta (ver VerificadorDeAuditoria#ultimaComprobacionPara). La empresa
     * de un movimiento es la de su fichaje.
     */

    /** Movimientos de esa empresa hasta un punto de control, con el hash recalculable o sin él. */
    @Query("select count(a) from auditoria_fichaje a where a.registro.empresa.id = :empresaId "
            + "and a.id <= :hastaId and a.versionHash >= :version")
    long contarComprobablesDeEmpresaHasta(
            @Param("empresaId") long empresaId, @Param("hastaId") long hastaId, @Param("version") short version);

    @Query("select count(a) from auditoria_fichaje a where a.registro.empresa.id = :empresaId "
            + "and a.id <= :hastaId and a.versionHash < :version")
    long contarSoloEnlaceDeEmpresaHasta(
            @Param("empresaId") long empresaId, @Param("hastaId") long hastaId, @Param("version") short version);

    /** Movimientos de esa empresa escritos después de un punto de control. */
    @Query("select count(a) from auditoria_fichaje a where a.registro.empresa.id = :empresaId and a.id > :hastaId")
    long contarDeEmpresaDesde(@Param("empresaId") long empresaId, @Param("hastaId") long hastaId);

    /** Línea temporal completa de un fichaje, más antiguo primero. */
    List<TimeEntryAudit> findByRegistro_IdOrderByFechaHoraAsc(long registroId);

    /**
     * Línea temporal de VARIOS fichajes a la vez, más antiguo primero.
     *
     * Hace falta porque una corrección (Fase 8) no sobrescribe: anula el
     * fichaje original y crea uno nuevo. Preguntar solo por un id
     * devolvería media historia -- ver
     * {@code TimeEntryServiceImpl#getAuditTrail}.
     */
    List<TimeEntryAudit> findByRegistro_IdInOrderByFechaHoraAsc(List<Long> registroIds);
}
