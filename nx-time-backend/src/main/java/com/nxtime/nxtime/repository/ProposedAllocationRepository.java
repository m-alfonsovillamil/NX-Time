package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.CorrectionRequest;
import com.nxtime.nxtime.domain.ProposedAllocation;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposedAllocationRepository extends JpaRepository<ProposedAllocation, Long> {

    List<ProposedAllocation> findBySolicitudOrderByIdAsc(CorrectionRequest solicitud);

    /**
     * El reparto propuesto de varias solicitudes en una consulta, para las
     * listas (la bandeja de pendientes y «mis correcciones»). Con el proyecto
     * de cada línea ya cargado: sin el grafo, cada proyecto distinto sería
     * otra consulta.
     */
    @EntityGraph(attributePaths = "proyecto")
    List<ProposedAllocation> findBySolicitud_IdInOrderByIdAsc(List<Long> solicitudIds);
}
