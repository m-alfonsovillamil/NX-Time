package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ScheduleException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleExceptionRepository extends JpaRepository<ScheduleException, Long> {

    List<ScheduleException> findByUsuario_IdAndFechaBetweenOrderByFechaAscInicioAsc(
            long usuarioId, LocalDate desde, LocalDate hasta);

    List<ScheduleException> findByEmpresa_IdAndFecha(long empresaId, LocalDate fecha);

    List<ScheduleException> findByUsuario_IdOrderByFechaDesc(long usuarioId);
}
