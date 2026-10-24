package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.InvitacionPendiente;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitacionPendienteRepository extends JpaRepository<InvitacionPendiente, Long> {

    /** Las de una empresa, por orden de llegada: se mandan en ese orden. */
    List<InvitacionPendiente> findByEmpresaIdOrderByCreadaEnAscUsuarioIdAsc(long empresaId, Pageable pagina);

    long countByEmpresaId(long empresaId);

    /** Quién de la empresa la tiene pendiente, para marcarlo en la plantilla con una sola consulta. */
    @Query("select i.usuarioId from invitaciones_pendientes i where i.empresaId = :empresaId")
    Set<Long> usuariosConInvitacionPendiente(@Param("empresaId") long empresaId);
}
