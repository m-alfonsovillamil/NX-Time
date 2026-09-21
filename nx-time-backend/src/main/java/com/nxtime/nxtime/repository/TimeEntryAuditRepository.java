package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.TimeEntryAudit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     */
    @Query(value = "SELECT pg_advisory_xact_lock(:clave)", nativeQuery = true)
    void bloquearCadena(@Param("clave") long clave);

    /**
     * Toda la traza en orden de escritura, para comprobar la cadena.
     *
     * Por id y no por fecha: el id es el orden en que se escribieron, que es
     * el orden en que se encadenaron los hashes. Dos filas de la misma
     * milésima se ordenarían al azar por fecha y la cadena parecería rota.
     */
    List<TimeEntryAudit> findAllByOrderByIdAsc();

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
