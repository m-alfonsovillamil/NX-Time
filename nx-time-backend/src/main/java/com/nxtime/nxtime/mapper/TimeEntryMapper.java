package com.nxtime.nxtime.mapper;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.SimpleUserDTO;
import com.nxtime.nxtime.dto.TeamTimeEntryDTO;
import com.nxtime.nxtime.dto.TimeEntryResponse;
import java.time.ZoneId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Sustituye a la función de extensión Kotlin
 * {@code Registros.toRegistroEquipoDTO()} y, desde esta fase, también
 * sustituye la exposición directa de la entidad TimeEntry en los
 * endpoints de fichaje (ver auditoría, defecto #1).
 *
 * minutosPausaAcumulados se DERIVA de segundosPausaAcumulados en un
 * único cálculo sobre el total real acumulado, en vez de sumar minutos
 * truncados por cada pausa individual (ver TimeEntry, y auditoría).
 *
 * segundosAMinutos() va marcado con @Named y se referencia con
 * qualifiedByName (no con expression="java(...)"): con expression,
 * MapStruct trataba el método como conversor implícito genérico
 * "long -> long" y lo aplicaba también a "id" (que también es long),
 * truncando id=1 a id=1/60=0 -- bug real, detectado por los tests de
 * contrato. @Named + qualifiedByName lo restringe a donde se pide
 * explícitamente.
 *
 * "fecha" (TeamTimeEntryDTO) se deriva del Instant horaEntrada
 * proyectado a MADRID_ZONE (Fase 3: horaEntrada dejó de ser
 * LocalDateTime "ingenuo" para ser un Instant real -- necesita zona
 * explícita para saber a qué día de calendario pertenece).
 */
@Mapper(componentModel = "spring")
public interface TimeEntryMapper {

    ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");

    @Mapping(target = "minutosPausaAcumulados", source = "segundosPausaAcumulados", qualifiedByName = "segundosAMinutos")
    TimeEntryResponse toResponse(TimeEntry entry);

    @Mapping(target = "fecha", expression = "java(entry.getHoraEntrada().atZone(MADRID_ZONE).toLocalDate())")
    @Mapping(target = "minutosPausaAcumulados", source = "segundosPausaAcumulados", qualifiedByName = "segundosAMinutos")
    TeamTimeEntryDTO toTeamDTO(TimeEntry entry);

    default SimpleUserDTO toSimpleUserDTO(User user) {
        return new SimpleUserDTO(user.getNombre());
    }

    @Named("segundosAMinutos")
    default long segundosAMinutos(long segundosPausaAcumulados) {
        return segundosPausaAcumulados / 60;
    }
}
