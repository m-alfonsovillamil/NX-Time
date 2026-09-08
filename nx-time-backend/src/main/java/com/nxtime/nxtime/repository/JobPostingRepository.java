package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.JobPosting;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    /**
     * Las ofertas PUBLICADAS de una empresa, para la plantilla.
     *
     * No filtra por fecha de cierre: una oferta cuyo plazo venció sigue
     * saliendo, marcada como fuera de plazo. Esconderla dejaría a quien
     * la vio ayer preguntándose si se la imaginó, y quien tiene una
     * candidatura presentada necesita seguir encontrando el sitio donde
     * la presentó. Quién puede optar lo decide {@code admiteCandidaturas()}.
     */
    @Query("SELECT o FROM ofertas_internas o LEFT JOIN FETCH o.departamento "
            + "WHERE o.empresa.id = :empresaId "
            + "AND o.estado = com.nxtime.nxtime.domain.JobPostingStatus.ABIERTA "
            + "ORDER BY o.fechaPublicacion DESC")
    List<JobPosting> findPublicadas(@Param("empresaId") long empresaId);

    /** Todas las de la empresa, borradores incluidos: la vista de gestión. */
    @Query("SELECT o FROM ofertas_internas o LEFT JOIN FETCH o.departamento "
            + "JOIN FETCH o.publicadaPor "
            + "WHERE o.empresa.id = :empresaId "
            + "ORDER BY CASE WHEN o.estado = com.nxtime.nxtime.domain.JobPostingStatus.ABIERTA "
            + "  THEN 0 WHEN o.estado = com.nxtime.nxtime.domain.JobPostingStatus.BORRADOR "
            + "  THEN 1 ELSE 2 END ASC, o.creadoEn DESC")
    List<JobPosting> findDeEmpresa(@Param("empresaId") long empresaId);

    @Query("SELECT o FROM ofertas_internas o LEFT JOIN FETCH o.departamento "
            + "JOIN FETCH o.publicadaPor WHERE o.id = :id")
    Optional<JobPosting> findConDetalle(@Param("id") long id);
}
