package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Company;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Las consultas del panel de plataforma (ADR 040): la instalación entera,
 * empresa por empresa.
 *
 * <b>Es el único repositorio que mira a través de las empresas</b>, y solo lo
 * usa {@code PlatformServiceImpl}, detrás de {@code plataforma:ver}. Todo lo
 * que devuelve son cifras, salvo {@link #administradores}: ni un fichaje ni un
 * dato de ningún empleado.
 *
 * Nativas, como las de la analítica, y con la misma regla: una consulta por
 * pregunta y ninguna montada a trozos. <b>Las de la lista agregan por empresa
 * ({@code GROUP BY}) para toda la página a la vez</b>: una consulta por
 * empresa serían veinticinco por cada página, y con el tope de 30 s por
 * consulta del ADR 039 dejarían de responder en cuanto hubiera volumen.
 *
 * Las que cuentan fichajes entran siempre por {@code empresa_id} y un rango de
 * {@code hora_entrada}: es el índice {@code idx_registros_empresa_entrada}. Por
 * eso los totales de la instalación se suman empresa a empresa en vez de
 * recorrer la tabla por fecha, que no tiene índice.
 */
public interface PlatformRepository extends Repository<Company, Long> {

    /**
     * Una página de empresas, con lo que hace falta para ordenarlas.
     *
     * El orden va en un parámetro y no en el texto: cada {@code CASE} vale
     * para un orden y es nulo en los demás, así que solo ordena el que se pide.
     * El desempate por nombre e id evita que una fila salte de página.
     *
     * {@code strpos} y no {@code LIKE}: lo que se teclea se busca tal cual, sin
     * tener que escapar los comodines.
     */
    @Query(value = """
            SELECT e.id AS id,
                   e.nombre AS nombre,
                   e.zona_horaria AS zonaHoraria,
                   e.creada_en AS creadaEn,
                   COALESCE(p.activos, 0) AS empleadosActivos,
                   COALESCE(p.de_baja, 0) AS empleadosDeBaja,
                   COALESCE(p.sin_confirmar, 0) AS sinConfirmar,
                   f.ultimo AS ultimoFichaje
            FROM empresas e
            LEFT JOIN (
                SELECT u.empresa_id,
                       COUNT(*) FILTER (WHERE u.activo) AS activos,
                       COUNT(*) FILTER (WHERE NOT u.activo) AS de_baja,
                       COUNT(*) FILTER (WHERE u.correo_sin_confirmar_desde IS NOT NULL) AS sin_confirmar
                FROM usuarios u
                GROUP BY u.empresa_id
            ) p ON p.empresa_id = e.id
            LEFT JOIN LATERAL (
                SELECT r.hora_entrada AS ultimo
                FROM registros r
                WHERE r.empresa_id = e.id AND r.anulado = false
                ORDER BY r.hora_entrada DESC
                LIMIT 1
            ) f ON TRUE
            WHERE :busqueda = '' OR strpos(LOWER(e.nombre), LOWER(:busqueda)) > 0
            ORDER BY CASE WHEN :orden = 'ALTA' THEN e.creada_en END DESC NULLS LAST,
                     CASE WHEN :orden = 'EMPLEADOS' THEN COALESCE(p.activos, 0) END DESC NULLS LAST,
                     CASE WHEN :orden = 'ACTIVIDAD' THEN f.ultimo END DESC NULLS LAST,
                     LOWER(e.nombre), e.id
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM empresas e
            WHERE :busqueda = '' OR strpos(LOWER(e.nombre), LOWER(:busqueda)) > 0
            """,
            nativeQuery = true)
    Page<EmpresaProjection> empresas(
            @Param("busqueda") String busqueda, @Param("orden") String orden, Pageable pagina);

    /** Los fichajes recientes de las empresas de una página, sin los anulados. */
    @Query(value = """
            SELECT r.empresa_id AS empresaId,
                   COUNT(*) FILTER (WHERE r.hora_entrada >= :hace7) AS en7Dias,
                   COUNT(*) AS en30Dias,
                   COUNT(DISTINCT r.usuario_id) AS personas
            FROM registros r
            WHERE r.empresa_id IN (:empresaIds)
              AND r.anulado = false
              AND r.hora_entrada >= :hace30
            GROUP BY r.empresa_id
            """, nativeQuery = true)
    List<FichajesProjection> fichajesRecientes(
            @Param("empresaIds") Collection<Long> empresaIds,
            @Param("hace7") Instant hace7,
            @Param("hace30") Instant hace30);

    /**
     * La última sesión de alguien de cada empresa de una página.
     *
     * Cada renovación de la sesión escribe un refresh token nuevo (ADR 030),
     * así que el último que se creó es la última vez que alguien usó la
     * aplicación, no solo la última vez que escribió su contraseña.
     */
    @Query(value = """
            SELECT u.empresa_id AS empresaId, MAX(t.creado_en) AS ultima
            FROM refresh_tokens t
            JOIN usuarios u ON u.id = t.usuario_id
            WHERE u.empresa_id IN (:empresaIds)
            GROUP BY u.empresa_id
            """, nativeQuery = true)
    List<UltimaSesionProjection> ultimasSesiones(@Param("empresaIds") Collection<Long> empresaIds);

    // ------------------------------------------------------------------
    // El detalle de una empresa
    // ------------------------------------------------------------------

    /** La plantilla de una empresa, rol por rol. */
    @Query(value = """
            SELECT u.rol AS rol,
                   COUNT(*) FILTER (WHERE u.activo) AS activos,
                   COUNT(*) FILTER (WHERE NOT u.activo) AS deBaja
            FROM usuarios u
            WHERE u.empresa_id = :empresaId
            GROUP BY u.rol
            """, nativeQuery = true)
    List<PlantillaProjection> plantilla(@Param("empresaId") long empresaId);

    /**
     * A quién escribir: los ADMIN en activo de una empresa.
     *
     * Son los únicos datos de personas que salen de aquí. Quien presta el
     * servicio necesita poder dirigirse a quien lo contrató, y ese es el ADMIN.
     */
    @Query(value = """
            SELECT TRIM(u.nombre || ' ' || COALESCE(u.apellidos, '')) AS nombre,
                   u.email AS email,
                   u.correo_sin_confirmar_desde IS NOT NULL AS sinConfirmar
            FROM usuarios u
            WHERE u.empresa_id = :empresaId AND u.rol = 'ADMIN' AND u.activo
            ORDER BY u.id
            """, nativeQuery = true)
    List<AdministradorProjection> administradores(@Param("empresaId") long empresaId);

    /** Los fichajes de una empresa, sin los anulados: el último y cuántos. */
    @Query(value = """
            SELECT MAX(r.hora_entrada) AS ultimo,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE r.hora_entrada >= :hace7) AS en7Dias,
                   COUNT(*) FILTER (WHERE r.hora_entrada >= :hace30) AS en30Dias,
                   COUNT(DISTINCT r.usuario_id) FILTER (WHERE r.hora_entrada >= :hace30) AS personas
            FROM registros r
            WHERE r.empresa_id = :empresaId AND r.anulado = false
            """, nativeQuery = true)
    FichajesDeEmpresaProjection fichajesDe(
            @Param("empresaId") long empresaId, @Param("hace7") Instant hace7, @Param("hace30") Instant hace30);

    /**
     * Las sesiones de una empresa: la última, y cuántas se han abierto en los
     * últimos treinta días desde la web y desde la app. Una sesión es una
     * familia de refresh tokens: renovarla no cuenta como otra.
     */
    @Query(value = """
            SELECT MAX(t.creado_en) AS ultima,
                   COUNT(DISTINCT t.familia) FILTER (WHERE t.origen = 'WEB' AND t.creado_en >= :hace30) AS web,
                   COUNT(DISTINCT t.familia) FILTER (WHERE t.origen <> 'WEB' AND t.creado_en >= :hace30) AS app
            FROM refresh_tokens t
            JOIN usuarios u ON u.id = t.usuario_id
            WHERE u.empresa_id = :empresaId
            """, nativeQuery = true)
    SesionesProjection sesionesDe(@Param("empresaId") long empresaId, @Param("hace30") Instant hace30);

    /** Lo demás que se cuenta de una empresa, cada cosa de su tabla. */
    @Query(value = """
            SELECT (SELECT COUNT(*) FROM identidades_externas i JOIN usuarios u ON u.id = i.usuario_id
                    WHERE u.empresa_id = :empresaId AND i.proveedor = 'GOOGLE') AS conGoogle,
                   (SELECT COUNT(*) FROM identidades_externas i JOIN usuarios u ON u.id = i.usuario_id
                    WHERE u.empresa_id = :empresaId AND i.proveedor = 'MICROSOFT') AS conMicrosoft,
                   (SELECT COUNT(*) FROM kioscos k
                    WHERE k.empresa_id = :empresaId AND k.revocado_en IS NULL) AS kioscos,
                   (SELECT COUNT(*) FROM dispositivos_push d JOIN usuarios u ON u.id = d.usuario_id
                    WHERE u.empresa_id = :empresaId) AS dispositivosPush,
                   (SELECT COUNT(*) FROM departamentos d WHERE d.empresa_id = :empresaId) AS departamentos,
                   (SELECT COUNT(*) FROM proyectos p WHERE p.empresa_id = :empresaId AND p.activo) AS proyectos,
                   (SELECT COALESCE(SUM(a.tamano_bytes), 0)::BIGINT FROM adjuntos a
                    WHERE a.empresa_id = :empresaId) AS bytesDeAdjuntos,
                   (SELECT COUNT(*) FROM solicitudes_borrado s
                    WHERE s.empresa_id = :empresaId AND s.estado = 'PENDIENTE') AS borradosPendientes,
                   (SELECT COUNT(*) FROM auditoria_fichaje a JOIN registros r ON r.id = a.registro_id
                    WHERE r.empresa_id = :empresaId) AS movimientosDeAuditoria
            """, nativeQuery = true)
    RecuentosProjection recuentosDe(@Param("empresaId") long empresaId);

    // ------------------------------------------------------------------
    // La instalación
    // ------------------------------------------------------------------

    /**
     * Cuántas empresas hay, cuántas han fichado en los últimos treinta días y
     * cuántos fichajes van hoy. Empresa a empresa, por el índice: ver arriba.
     */
    @Query(value = """
            SELECT COUNT(*) AS empresas,
                   COUNT(*) FILTER (WHERE c.en30 > 0) AS empresasConActividad,
                   COALESCE(SUM(c.hoy), 0)::BIGINT AS fichajesHoy
            FROM empresas e
            CROSS JOIN LATERAL (
                SELECT COUNT(*) AS en30,
                       COUNT(*) FILTER (WHERE r.hora_entrada >= :hoy) AS hoy
                FROM registros r
                WHERE r.empresa_id = e.id AND r.anulado = false AND r.hora_entrada >= :hace30
            ) c
            """, nativeQuery = true)
    ActividadProjection actividad(@Param("hoy") Instant hoy, @Param("hace30") Instant hace30);

    /** Las cuentas de la instalación: en activo, y registros que se quedaron sin confirmar. */
    @Query(value = """
            SELECT COUNT(*) FILTER (WHERE u.activo) AS activas,
                   COUNT(*) FILTER (WHERE u.correo_sin_confirmar_desde IS NOT NULL) AS sinConfirmar
            FROM usuarios u
            """, nativeQuery = true)
    CuentasProjection cuentas();

    /**
     * Las altas de empresas por semana desde una fecha. La semana es la de
     * Madrid, que es desde donde se mira esto, y empieza en lunes. Solo salen
     * las semanas con alguna alta: los ceros los pone el servicio.
     */
    @Query(value = """
            SELECT date_trunc('week', e.creada_en AT TIME ZONE 'Europe/Madrid')::date AS semana,
                   COUNT(*) AS altas
            FROM empresas e
            WHERE e.creada_en >= :desde
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<AltasProjection> altasPorSemana(@Param("desde") Instant desde);

    // ------------------------------------------------------------------

    interface EmpresaProjection {
        long getId();

        String getNombre();

        String getZonaHoraria();

        Instant getCreadaEn();

        long getEmpleadosActivos();

        long getEmpleadosDeBaja();

        long getSinConfirmar();

        Instant getUltimoFichaje();
    }

    interface FichajesProjection {
        long getEmpresaId();

        long getEn7Dias();

        long getEn30Dias();

        long getPersonas();
    }

    interface UltimaSesionProjection {
        long getEmpresaId();

        Instant getUltima();
    }

    interface PlantillaProjection {
        String getRol();

        long getActivos();

        long getDeBaja();
    }

    interface AdministradorProjection {
        String getNombre();

        String getEmail();

        boolean getSinConfirmar();
    }

    interface FichajesDeEmpresaProjection {
        Instant getUltimo();

        long getTotal();

        long getEn7Dias();

        long getEn30Dias();

        long getPersonas();
    }

    interface SesionesProjection {
        Instant getUltima();

        long getWeb();

        long getApp();
    }

    interface RecuentosProjection {
        long getConGoogle();

        long getConMicrosoft();

        long getKioscos();

        long getDispositivosPush();

        long getDepartamentos();

        long getProyectos();

        long getBytesDeAdjuntos();

        long getBorradosPendientes();

        long getMovimientosDeAuditoria();
    }

    interface ActividadProjection {
        long getEmpresas();

        long getEmpresasConActividad();

        long getFichajesHoy();
    }

    interface CuentasProjection {
        long getActivas();

        long getSinConfirmar();
    }

    interface AltasProjection {
        LocalDate getSemana();

        long getAltas();
    }
}
