package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.EquipoAhoraResponse;

/** Quién está trabajando ahora y quién no ha fichado hoy (plan N, paso N7). */
public interface EquipoAhoraService {

    /** La plantilla activa de la empresa de {@code actor}, en este momento. */
    EquipoAhoraResponse ahora(User actor);
}
