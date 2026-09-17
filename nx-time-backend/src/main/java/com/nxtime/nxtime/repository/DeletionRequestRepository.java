package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.DeletionRequest;
import com.nxtime.nxtime.domain.DeletionStatus;
import com.nxtime.nxtime.domain.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeletionRequestRepository extends JpaRepository<DeletionRequest, Long> {

    /** La más reciente de una persona, sea cual sea su estado: es lo que ve en Ajustes. */
    Optional<DeletionRequest> findFirstByUsuarioOrderByCreadaEnDesc(User usuario);

    Optional<DeletionRequest> findByUsuarioAndEstado(User usuario, DeletionStatus estado);

    List<DeletionRequest> findByEmpresa_IdAndEstadoOrderByCreadaEnAsc(long empresaId, DeletionStatus estado);

    long countByEmpresa_IdAndEstado(long empresaId, DeletionStatus estado);

    /** Las ejecutadas cuyo plazo de conservación ya ha vencido y siguen sin anonimizar. */
    @Query("""
            SELECT s FROM solicitudes_borrado s
            WHERE s.estado = com.nxtime.nxtime.domain.DeletionStatus.EJECUTADA
              AND s.anonimizadaEn IS NULL
              AND s.anonimizarDesde <= :hoy
            """)
    List<DeletionRequest> findPorAnonimizar(@Param("hoy") LocalDate hoy);

    // ------------------------------------------------------------------
    // Lo que impide ejecutar un borrado (ver DataDeletionServiceImpl.bloqueos)
    // ------------------------------------------------------------------
    // Van aquí, y no repartidas por los repositorios de cada módulo, para
    // que la lista de "qué tiene que estar cerrado" se lea de una vez.

    @Query("SELECT COUNT(r) FROM registros r WHERE r.usuario.id = :usuarioId AND r.horaSalida IS NULL")
    long contarJornadasAbiertas(@Param("usuarioId") long usuarioId);

    /** Pedidas por la persona o sobre sus fichajes: en las dos le toca algo. */
    @Query("""
            SELECT COUNT(c) FROM solicitudes_correccion c
            WHERE (c.solicitante.id = :usuarioId OR c.registro.usuario.id = :usuarioId)
              AND c.estado IN (com.nxtime.nxtime.domain.CorrectionStatus.PENDIENTE,
                               com.nxtime.nxtime.domain.CorrectionStatus.EN_DISPUTA)
            """)
    long contarCorreccionesVivas(@Param("usuarioId") long usuarioId);

    @Query("""
            SELECT COUNT(a) FROM peticiones_ausencia a
            WHERE a.usuario.id = :usuarioId
              AND a.estado = com.nxtime.nxtime.domain.AbsenceStatus.PENDIENTE
            """)
    long contarAusenciasPendientes(@Param("usuarioId") long usuarioId);

    /** Solo las identificadas: de las anónimas no hay forma de saber quién las puso. */
    @Query("""
            SELECT COUNT(d) FROM denuncias d
            WHERE d.denunciante.id = :usuarioId
              AND d.estado IN (com.nxtime.nxtime.domain.ComplaintStatus.RECIBIDA,
                               com.nxtime.nxtime.domain.ComplaintStatus.EN_INVESTIGACION)
            """)
    long contarDenunciasAbiertas(@Param("usuarioId") long usuarioId);

    @Query("""
            SELECT COUNT(u) FROM usuarios u
            WHERE u.empresa.id = :empresaId AND u.activo = TRUE
              AND u.rol = com.nxtime.nxtime.domain.Role.ADMIN
            """)
    long contarAdminsActivos(@Param("empresaId") long empresaId);

    /** La entrada del último fichaje: de ahí se cuentan los 4 años. */
    @Query("SELECT MAX(r.horaEntrada) FROM registros r WHERE r.usuario.id = :usuarioId")
    Optional<Instant> ultimaEntrada(@Param("usuarioId") long usuarioId);
}
