package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.DisputeRequest;
import com.nxtime.nxtime.dto.ResolveCorrectionRequest;
import java.util.List;

/**
 * Solicitudes de corrección de fichajes (Fase E).
 *
 * <b>Ninguna corrección se aplica sola.</b> El fichaje no se toca hasta
 * que alguien aprueba, y quién es ese alguien depende de quién pidió la
 * corrección (ver {@link com.nxtime.nxtime.domain.CorrectionRequest} y
 * el ADR 010).
 */
public interface CorrectionService {

    /**
     * Pide corregir las horas de un fichaje.
     *
     * Se auto-aprueba —y por tanto se aplica en el acto— solo si quien
     * pide es el dueño del fichaje Y tiene {@code correccion:aprobar}.
     * En cualquier otro caso queda PENDIENTE.
     */
    CorrectionResponse solicitar(long fichajeId, CorrectionRequestDTO request, User actor);

    /**
     * Aprueba o rechaza. Aprobar es lo que aplica la corrección: anula el
     * fichaje original y crea el corregido.
     */
    CorrectionResponse resolver(long correccionId, ResolveCorrectionRequest request, User actor);

    /**
     * El dueño del fichaje no acepta la corrección que le proponen.
     *
     * No la cierra: la escala a RRHH. Solo puede disputar el dueño, y
     * solo una solicitud que pidió otra persona — disputar la tuya
     * propia no significaría nada.
     */
    CorrectionResponse disputar(long correccionId, DisputeRequest request, User actor);

    /** Las que le toca resolver a quien pregunta, ya sean suyas o de su equipo. */
    List<CorrectionResponse> pendientesParaMi(User actor);

    /** Las que ha pedido, para ver en qué han quedado. */
    List<CorrectionResponse> mias(User actor);
}
