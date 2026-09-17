package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AddPauseRequest;
import com.nxtime.nxtime.dto.AddedPauseDTO;
import com.nxtime.nxtime.dto.CorrectionResponse;
import java.util.List;

/**
 * Pausas añadidas a posteriori (ADR 015).
 *
 * <b>Quien decide si se aplica en el acto o pasa por aprobación es este
 * servicio, no la app.</b> La regla:
 *
 * <ul>
 *   <li>Jornada <b>abierta</b> → directa, aunque empezara ayer (un turno de
 *       noche sigue siendo "mi jornada de ahora").</li>
 *   <li>Jornada <b>cerrada cuya entrada cae hoy</b> en Madrid → directa.</li>
 *   <li>Cualquier otra → se pide como corrección, con su aprobación.</li>
 * </ul>
 *
 * Lo que hace defendible la vía directa: añadir una pausa solo puede
 * <b>bajar</b> el tiempo trabajado, nunca subirlo, queda en la traza con
 * su motivo, y nunca aplica sobre el fichaje de otra persona.
 */
public interface AddedPauseService {

    /**
     * Lo que ha pasado al añadir: o se aplicó y aquí está el fichaje, o se
     * pidió como corrección y aquí está la solicitud. Nunca los dos.
     */
    record Resultado(TimeEntry fichaje, CorrectionResponse correccion) {

        public boolean aplicada() {
            return fichaje != null || correccion.estado() == com.nxtime.nxtime.domain.CorrectionStatus.APROBADA;
        }
    }

    Resultado anadir(long fichajeId, AddPauseRequest request, User actor);

    List<AddedPauseDTO> deLaJornada(long fichajeId, User actor);

    /** Deshace una pausa añadida. Solo sobre la jornada abierta, y solo su dueño. */
    TimeEntry anular(long fichajeId, long pausaId, User actor);
}
