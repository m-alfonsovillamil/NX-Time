package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.ComplaintCategory;
import com.nxtime.nxtime.domain.ComplaintStatus;
import java.time.Instant;
import java.util.List;

/**
 * Un expediente completo (Fase G): la denuncia y su conversación.
 *
 * Lo devuelven las dos puertas del canal — la del denunciante, que
 * entra con su código, y la de quien instruye — y devuelven <b>lo
 * mismo</b>. No hay campos que solo vea el instructor: si los hubiera,
 * habría una parte del expediente que se tramita a espaldas de quien
 * denunció, y el canal dejaría de ser el sitio donde uno puede seguir
 * qué está pasando con lo que contó.
 *
 * <b>Los plazos van calculados, no guardados.</b> {@code diasHastaAcuse}
 * y {@code diasHastaRespuesta} son los dos del art. 9.2 de la Ley
 * 2/2023 (7 días naturales y 3 meses), y llegan en negativo cuando ya
 * se han pasado — que es precisamente la información que hay que
 * enseñar, no ocultar tras un cero. Cada uno se apaga (null) cuando su
 * hecho ya ocurrió: el del acuse en cuanto hay acuse, el de la
 * respuesta en cuanto el expediente se cierra.
 */
public record ComplaintResponse(
        long id,
        ComplaintCategory categoria,
        String categoriaEtiqueta,
        String descripcion,
        ComplaintStatus estado,
        boolean anonima,

        /** El nombre de quien denunció, o null si la denuncia es anónima. */
        String denunciante,

        Instant creadoEn,
        Instant acuseReciboEn,
        Instant resueltaEn,
        String conclusion,

        /** Días que quedan para acusar recibo; negativo si ya pasó el plazo. */
        Long diasHastaAcuse,

        /** Días que quedan para responder; negativo si ya pasó el plazo. */
        Long diasHastaRespuesta,

        List<ComplaintMessageResponse> mensajes
) {
}
