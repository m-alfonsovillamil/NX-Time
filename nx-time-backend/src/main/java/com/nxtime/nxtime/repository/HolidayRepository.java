package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.Holiday;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /**
     * Festivos aplicables a una empresa en un rango: los suyos propios
     * MÁS los nacionales ({@code empresa IS NULL}, ver {@link Holiday}).
     * Un festivo nacional se guarda una sola vez, no una fila por
     * empresa, así que hay que unir ambos casos explícitamente.
     *
     * Ordenados por fecha desde la Fase C: es el listado que se pinta
     * bajo el calendario del mes, y ordenar una lista ya ordenada en la
     * base costaría otro recorrido en Java para nada.
     */
    @Query("SELECT h FROM festivos h WHERE h.fecha BETWEEN :desde AND :hasta "
            + "AND (h.empresa IS NULL OR h.empresa.id = :empresaId) "
            + "ORDER BY h.fecha")
    List<Holiday> findAplicables(
            @Param("empresaId") long empresaId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /**
     * Si ya están sembrados los festivos nacionales de un año.
     *
     * Lo pregunta {@link com.nxtime.nxtime.service.NationalHolidaySeeder}
     * antes de generarlos. Se cuenta sobre el ámbito y no sobre
     * {@code empresa IS NULL} pudiendo ser lo mismo porque lo que se
     * quiere saber es "¿está hecha la siembra de este año?", y esa
     * pregunta la responde el ámbito: si mañana un nacional pudiera
     * guardarse de otra forma, esta consulta seguiría diciendo la verdad.
     */
    @Query("SELECT COUNT(h) > 0 FROM festivos h "
            + "WHERE h.ambito = com.nxtime.nxtime.domain.HolidayScope.NACIONAL "
            + "AND YEAR(h.fecha) = :anio")
    boolean existenNacionalesDelAnio(@Param("anio") int anio);

    /**
     * Un festivo YA existente de esa empresa en esa fecha, para dar un
     * 409 con mensaje en vez de dejar que reviente
     * {@code uq_festivos_empresa_fecha} como un 500 (mismo criterio que
     * {@code DepartmentServiceImpl.crear}).
     */
    @Query("SELECT h FROM festivos h WHERE h.empresa.id = :empresaId AND h.fecha = :fecha")
    Optional<Holiday> findDeEmpresaEnFecha(
            @Param("empresaId") long empresaId, @Param("fecha") LocalDate fecha);

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
