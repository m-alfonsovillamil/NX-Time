package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ScheduleTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleTemplateRepository extends JpaRepository<ScheduleTemplate, Long> {

    List<ScheduleTemplate> findByEmpresa_IdOrderByNombreAsc(long empresaId);
}
