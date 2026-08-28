package com.nxtime.nxtime.mapper;

import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.SimpleUserDTO;
import com.nxtime.nxtime.dto.TimeEntryAuditResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapea TimeEntryAudit a la línea temporal que devuelve AuditController.
 */
@Mapper(componentModel = "spring")
public interface TimeEntryAuditMapper {

    @Mapping(target = "registroId", source = "registro.id")
    TimeEntryAuditResponse toResponse(TimeEntryAudit audit);

    default SimpleUserDTO toSimpleUserDTO(User user) {
        return new SimpleUserDTO(user.getNombre());
    }
}
