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
 *
 * Desde la fase J6 (ADR 048) el acuse es automático al presentarla, así que
 * {@code diasHastaAcuse} solo sale en las de antes; y hay un tercer plazo,
 * {@code diasHastaSupresion}: sin investigación, a los tres meses el
 * contenido se suprime.
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

        /** Días que quedan para responder; negativo si ya pasó el plazo. Seis meses si se prorrogó. */
        Long diasHastaRespuesta,

        List<ComplaintMessageResponse> mensajes,

        /** Domicilio, correo o lugar seguro que dio quien denunció (art. 7.2), o null. */
        String contactoNotificaciones,

        Instant investigacionIniciadaEn,

        /** Cuándo se amplió la respuesta a seis meses (art. 9.2.d), o null. */
        Instant respuestaProrrogadaEn,

        /** Cuándo se suprimió el contenido (art. 32.3), o null. */
        Instant contenidoSuprimidoEn,

        /**
         * Días que quedan para que se suprima el contenido si no se pasa a
         * investigación (art. 32.3); null si ya se investiga o ya se suprimió.
         */
        Long diasHastaSupresion
) {
}
