package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    Optional<TimeEntry> findByUsuarioAndHoraSalidaIsNull(User usuario);

    /**
     * JOIN FETCH evita el N+1 al cargar el usuario de cada fichaje (ver
     * auditoría). Pageable acota el resultado -- antes /historial
     * devolvía la tabla entera sin límite. "t.anulado = false" (Fase 8):
     * una vez corregido un fichaje, la fila original deja de aparecer
     * en el historial -- la fila nueva (correcta) sí, y la traza de
     * ambas queda en TimeEntryAudit, no se pierde.
     */
    @Query("SELECT t FROM registros t JOIN FETCH t.usuario "
            + "WHERE t.usuario = :usuario AND t.anulado = false ORDER BY t.horaEntrada DESC")
    List<TimeEntry> findHistoryByUsuario(@Param("usuario") User usuario, Pageable pageable);

    /**
     * Antes usaba findByEmpresa (TODOS los usuarios de la empresa,
     * incluidos otros gestores -- ver auditoría, hueco de aislamiento
     * multi-tenant). Aquí se filtra explícitamente por rol EMPLEADO, de
     * modo que "el historial del equipo" de un gestor sea solo el de
     * sus empleados. Filtra por t.empresa directo (denormalizado desde
     * la Fase 3), no por u.empresa: aprovecha el índice
     * (empresa_id, hora_entrada) de "registros" en vez de forzar el
     * join a "usuarios" para el filtro de tenant. "t.anulado = false"
     * (Fase 8): ver findHistoryByUsuario.
     */
    @Query("SELECT t FROM registros t JOIN FETCH t.usuario u "
            + "WHERE t.empresa = :empresa AND u.rol = com.nxtime.nxtime.domain.Role.EMPLEADO "
            + "AND t.anulado = false ORDER BY t.horaEntrada DESC")
    List<TimeEntry> findTeamHistory(@Param("empresa") Company empresa, Pageable pageable);

    /**
     * Jornadas todavía abiertas cuya entrada es anterior al límite dado:
     * las que nadie cerró (Fase 9, ver IncompleteTimeEntryScheduler).
     * Excluye las ya anuladas por una corrección, que no hay que tocar.
     */
    @Query("SELECT t FROM registros t JOIN FETCH t.usuario "
            + "WHERE t.horaSalida IS NULL AND t.anulado = false AND t.horaEntrada < :limite")
    List<TimeEntry> findJornadasAbiertasAnterioresA(@Param("limite") Instant limite);

    // ------------------------------------------------------------------
    // Agregados del dashboard (Fase 10)
    //
    // Se calculan con GROUP BY en la base de datos, NO cargando las
    // entidades y sumando en Java: el histórico de un empleado son
    // cientos de filas y el de una empresa, miles. Traerlas todas a
    // memoria para reducirlas a un número es justo lo que no hay que
    // hacer (ver auditoría del plan).
    //
    // Son consultas NATIVAS porque JPQL no tiene forma de restar dos
    // instantes: EXTRACT(EPOCH FROM (hora_salida - hora_entrada)) es
    // específico de PostgreSQL. Es una decisión consciente -- el
    // proyecto está casado con PostgreSQL desde la Fase 3 (índices
    // parciales, JSONB, GRANTs), así que fingir portabilidad aquí sería
    // teatro.
    //
    // Solo cuentan jornadas CERRADAS (hora_salida IS NOT NULL) y no
    // anuladas: una jornada en curso todavía no ha producido horas, y
    // una anulada fue sustituida por su corrección (Fase 8).
    // ------------------------------------------------------------------

    /** Segundos netos (descontando pausas) trabajados por un usuario en un rango. */
    @Query(value = """
            SELECT COALESCE(SUM(
                       EXTRACT(EPOCH FROM (r.hora_salida - r.hora_entrada))
                       - r.segundos_pausa_acumulados), 0)
            FROM registros r
            WHERE r.usuario_id = :usuarioId
              AND r.anulado = false
              AND r.hora_salida IS NOT NULL
              AND r.hora_entrada >= :desde
              AND r.hora_entrada < :hasta
            """, nativeQuery = true)
    long sumarSegundosTrabajados(
            @Param("usuarioId") long usuarioId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /** Lo mismo, agregado para toda la empresa. */
    @Query(value = """
            SELECT COALESCE(SUM(
                       EXTRACT(EPOCH FROM (r.hora_salida - r.hora_entrada))
                       - r.segundos_pausa_acumulados), 0)
            FROM registros r
            WHERE r.empresa_id = :empresaId
              AND r.anulado = false
              AND r.hora_salida IS NOT NULL
              AND r.hora_entrada >= :desde
              AND r.hora_entrada < :hasta
            """, nativeQuery = true)
    long sumarSegundosTrabajadosEmpresa(
            @Param("empresaId") long empresaId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /**
     * Horas por empleado en un rango, de mayor a menor. Una sola
     * consulta con GROUP BY + JOIN, en vez de recorrer los empleados y
     * preguntar por cada uno.
     */
    @Query(value = """
            SELECT u.id                AS usuarioId,
                   u.nombre            AS nombre,
                   COALESCE(SUM(
                       EXTRACT(EPOCH FROM (r.hora_salida - r.hora_entrada))
                       - r.segundos_pausa_acumulados), 0) AS segundos
            FROM registros r
            JOIN usuarios u ON u.id = r.usuario_id
            WHERE r.empresa_id = :empresaId
              AND r.anulado = false
              AND r.hora_salida IS NOT NULL
              AND r.hora_entrada >= :desde
              AND r.hora_entrada < :hasta
            GROUP BY u.id, u.nombre
            ORDER BY segundos DESC
            """, nativeQuery = true)
    List<EmployeeHoursProjection> sumarSegundosPorEmpleado(
            @Param("empresaId") long empresaId,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    /**
     * Jornadas que cerró el proceso nocturno por no tener fichaje de
     * salida (Fase 9) y que siguen sin corregir: son las incidencias
     * abiertas que RRHH tiene pendientes.
     */
    @Query("SELECT COUNT(t) FROM registros t "
            + "WHERE t.empresa = :empresa AND t.jornadaIncompleta = true AND t.anulado = false")
    long contarIncidenciasAbiertas(@Param("empresa") Company empresa);

    /** Proyección de {@link #sumarSegundosPorEmpleado}: Spring Data la implementa sola. */
    interface EmployeeHoursProjection {
        long getUsuarioId();

        String getNombre();

        long getSegundos();
    }
}
