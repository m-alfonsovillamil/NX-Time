package com.nxtime.nxtime.mapper;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.SimpleUserDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Sustituye a la función de extensión Kotlin
 * {@code PeticionAusencia.toDTO()} que vivía en RespuestaAusencia.kt.
 *
 * "diasHabiles" (Fase 9) entra como segundo parámetro en vez de
 * derivarse de la entidad: calcularlo necesita el calendario laboral de
 * la empresa (ver WorkingDayService), que es una consulta a base de
 * datos -- no algo que un mapper deba hacer por su cuenta. Lo calcula
 * AbsenceServiceImpl y se lo pasa ya resuelto.
 */
@Mapper(componentModel = "spring")
public interface AbsenceMapper {

    @Mapping(target = "diasHabiles", source = "diasHabiles")
    AbsenceResponse toResponse(AbsenceRequest request, int diasHabiles);

    default SimpleUserDTO toSimpleUserDTO(User user) {
        return user == null ? null : new SimpleUserDTO(user.getNombre());
    }
}
