package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.CorrectionRequest;
import com.nxtime.nxtime.domain.ProposedAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposedAllocationRepository extends JpaRepository<ProposedAllocation, Long> {

    List<ProposedAllocation> findBySolicitudOrderByIdAsc(CorrectionRequest solicitud);

    List<ProposedAllocation> findBySolicitud_IdInOrderByIdAsc(List<Long> solicitudIds);
}
