package com.nxtime.nxtime.mapper;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.SimpleUserDTO;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import java.time.format.DateTimeFormatter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Sustituye a la función de extensión Kotlin
 * {@code Registros.toRegistroEquipoDTO()} que vivía en
 * ServicioFichajeImpl.kt.
 *
 * El formato "HH:mm:ss" / "yyyy-MM-dd" como String plano (en vez de
 * ISO-8601 tipado) se mantiene sin cambios en esta fase: es parte del
 * contrato HTTP actual, y la Fase 2 lo rediseña.
 */
@Mapper(componentModel = "spring")
public interface TimeEntryMapper {

    DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Mapping(target = "horaEntrada", expression = "java(entry.getHoraEntrada().format(TIME_FORMATTER))")
    @Mapping(target = "horaSalida",
            expression = "java(entry.getHoraSalida() != null ? entry.getHoraSalida().format(TIME_FORMATTER) : null)")
    @Mapping(target = "fecha", expression = "java(entry.getHoraEntrada().toLocalDate().format(DATE_FORMATTER))")
    TeamTimeEntryDTO toTeamDTO(TimeEntry entry);

    default SimpleUserDTO toSimpleUserDTO(User user) {
        return new SimpleUserDTO(user.getNombre());
    }
}
