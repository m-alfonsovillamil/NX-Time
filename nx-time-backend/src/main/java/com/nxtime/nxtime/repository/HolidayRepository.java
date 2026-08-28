package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Holiday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /**
     * Festivos aplicables a una empresa en un rango: los suyos propios
     * MÁS los nacionales ({@code empresa IS NULL}, ver {@link Holiday}).
     * Un festivo nacional se guarda una sola vez, no una fila por
     * empresa, así que hay que unir ambos casos explícitamente.
     */
    @Query("SELECT h FROM festivos h WHERE h.fecha BETWEEN :desde AND :hasta "
            + "AND (h.empresa IS NULL OR h.empresa.id = :empresaId)")
    List<Holiday> findAplicables(
            @Param("empresaId") long empresaId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Todos los festivos aplicables a una empresa en un año natural.
     * Es la consulta que cachea {@link
     * com.nxtime.nxtime.service.HolidayCalendar} (Fase 10): el año es
     * una clave de caché estable, a diferencia de un rango arbitrario
     * de fechas, que sería distinto en cada llamada y no se reutilizaría
     * nunca.
     */
    @Query("SELECT h FROM festivos h WHERE YEAR(h.fecha) = :anio "
            + "AND (h.empresa IS NULL OR h.empresa.id = :empresaId)")
    List<Holiday> findByEmpresaYAnio(@Param("empresaId") long empresaId, @Param("anio") int anio);
}
