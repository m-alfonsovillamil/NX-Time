package com.nxtime.nxtime.mapper;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.SimpleUserDTO;
import org.mapstruct.Mapper;

/**
 * Sustituye a la función de extensión Kotlin
 * {@code PeticionAusencia.toDTO()} que vivía en RespuestaAusencia.kt.
 */
@Mapper(componentModel = "spring")
public interface AbsenceMapper {

    AbsenceResponse toResponse(AbsenceRequest request);

    default SimpleUserDTO toSimpleUserDTO(User user) {
        return new SimpleUserDTO(user.getNombre());
    }
}
