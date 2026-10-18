package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.EslabonRetirado;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** Las lápidas del tramo común (V46, ADR 050). Solo se leen. */
public interface EslabonRetiradoRepository extends JpaRepository<EslabonRetirado, Long> {

    /** Las que vienen detrás de un id, en orden: se intercalan con las filas al recorrer el tramo común. */
    @Transactional(readOnly = true)
    List<EslabonRetirado> findByIdGreaterThanOrderByIdAsc(long id, Pageable bloque);

    boolean existsByIdAndHash(long id, String hash);
}
