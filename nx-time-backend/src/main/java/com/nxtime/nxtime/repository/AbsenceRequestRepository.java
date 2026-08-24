package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbsenceRequestRepository extends JpaRepository<AbsenceRequest, Long> {

    List<AbsenceRequest> findByUsuario(User usuario);

    List<AbsenceRequest> findByUsuario_Empresa_IdAndEstado(long empresaId, AbsenceStatus estado);

    List<AbsenceRequest> findByUsuario_Empresa_IdAndEstadoIsNot(long empresaId, AbsenceStatus estado);
}
