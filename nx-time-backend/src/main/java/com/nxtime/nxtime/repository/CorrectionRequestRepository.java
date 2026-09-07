package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.CorrectionRequest;
import com.nxtime.nxtime.domain.CorrectionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorrectionRequestRepository extends JpaRepository<CorrectionRequest, Long> {

    /**
     * La solicitud VIVA de un fichaje, si la hay.
     *
     * Se comprueba antes de crear otra para dar un 409 que se entienda;
     * quien lo garantiza de verdad es el índice único parcial
     * {@code uq_correcciones_una_viva_por_registro}, que además cubre la
     * carrera entre la lectura y el INSERT.
     */
    @Query("SELECT c FROM solicitudes_correccion c "
            + "WHERE c.registro.id = :registroId AND c.estado IN "
            + "(com.nxtime.nxtime.domain.CorrectionStatus.PENDIENTE, "
            + " com.nxtime.nxtime.domain.CorrectionStatus.EN_DISPUTA)")
    Optional<CorrectionRequest> findVivaDelRegistro(@Param("registroId") long registroId);

    /**
     * Las solicitudes vivas de una empresa, con todo lo que hace falta
     * para pintarlas y para decidir quién puede resolver cada una.
     *
     * El {@code JOIN FETCH} del solicitante y del dueño del fichaje no es
     * un adorno: <b>quién resuelve depende de quién pidió</b>, así que
     * ese dato se consulta para CADA fila. Sin él, listar diez
     * solicitudes dispararía veinte consultas más.
     */
    @Query("SELECT c FROM solicitudes_correccion c "
            + "JOIN FETCH c.solicitante "
            + "JOIN FETCH c.registro r JOIN FETCH r.usuario "
            + "WHERE c.empresa.id = :empresaId AND c.estado IN "
            + "(com.nxtime.nxtime.domain.CorrectionStatus.PENDIENTE, "
            + " com.nxtime.nxtime.domain.CorrectionStatus.EN_DISPUTA) "
            + "ORDER BY c.creadoEn ASC")
    List<CorrectionRequest> findVivasDeEmpresa(@Param("empresaId") long empresaId);

    /** Las que ha pedido una persona, para que vea en qué han quedado. */
    @Query("SELECT c FROM solicitudes_correccion c "
            + "JOIN FETCH c.registro r JOIN FETCH r.usuario "
            + "LEFT JOIN FETCH c.aprobador "
            + "WHERE c.solicitante.id = :usuarioId ORDER BY c.creadoEn DESC")
    List<CorrectionRequest> findMias(@Param("usuarioId") long usuarioId);

    /**
     * Las que afectan a los fichajes de una persona aunque las pidiera
     * otra: son las que ESA persona tiene que aprobar o disputar.
     */
    @Query("SELECT c FROM solicitudes_correccion c "
            + "JOIN FETCH c.solicitante "
            + "JOIN FETCH c.registro r JOIN FETCH r.usuario "
            + "WHERE r.usuario.id = :usuarioId AND c.estado = :estado "
            + "ORDER BY c.creadoEn ASC")
    List<CorrectionRequest> findSobreMisFichajes(
            @Param("usuarioId") long usuarioId, @Param("estado") CorrectionStatus estado);

    long countByEmpresa_IdAndEstado(long empresaId, CorrectionStatus estado);
}
