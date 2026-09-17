package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ProjectSegment;
import com.nxtime.nxtime.domain.TimeEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSegmentRepository extends JpaRepository<ProjectSegment, Long> {

    List<ProjectSegment> findByRegistroOrderByInicioAsc(TimeEntry registro);

    /** El tramo en curso. Como mucho uno: lo garantiza uq_tramos_uno_abierto_por_registro. */
    Optional<ProjectSegment> findByRegistroAndFinIsNull(TimeEntry registro);
}
