package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbsenceRequestRepository extends JpaRepository<AbsenceRequest, Long> {

    List<AbsenceRequest> findByUsuario(User usuario);

    // Filtra por AbsenceRequest.empresa directamente (denormalizado
    // desde la Fase 3) en vez de navegar usuario.empresa.id: más
    // simple y aprovecha el índice (empresa_id, estado) del esquema.
    List<AbsenceRequest> findByEmpresa_IdAndEstado(long empresaId, AbsenceStatus estado);

    List<AbsenceRequest> findByEmpresa_IdAndEstadoIsNot(long empresaId, AbsenceStatus estado);
}
