package com.nxtime.nxtime.repository;

import com.nxtime.nxtime.domain.ScheduleSlot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Long> {

    List<ScheduleSlot> findByPlantilla_IdOrderByDiaSemanaAscInicioAsc(long plantillaId);

    /** Los tramos de varias plantillas de una vez: el horario de un mes puede tocar dos. */
    List<ScheduleSlot> findByPlantilla_IdIn(Collection<Long> plantillaIds);

    /**
     * Borra los tramos de una plantilla con una sentencia, no entidad a
     * entidad, y no es por eficiencia: una sentencia DELETE se ejecuta en el
     * acto, mientras que borrar entidades encola los DELETE detrás de los
     * INSERT de los tramos nuevos, que chocarían con los viejos contra el
     * EXCLUDE de V31. Ver el Javadoc de {@code ScheduleTemplate}.
     */
    @Modifying
    @Query("DELETE FROM tramos_plantilla_horario t WHERE t.plantilla.id = :plantillaId")
    int borrarDeLaPlantilla(@Param("plantillaId") long plantillaId);
}
