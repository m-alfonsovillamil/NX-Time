package com.nxtime.nxtime.dto;

import java.time.Instant;

/**
 * La única vez que el código de seguimiento sale del servidor (Fase G).
 *
 * De él solo se guarda el hash, así que esta respuesta no se puede
 * volver a generar: no hay endpoint que lo reenvíe, ni por correo, ni
 * "solo al dueño". Que exista tal endpoint sería poder demostrar que
 * una denuncia anónima es tuya, que es justo lo que el canal promete
 * que no ocurrirá.
 *
 * Por eso viaja {@code avisoImportante}: el texto que la pantalla tiene
 * que enseñar antes de dejar cerrar el diálogo. Va en el servidor y no
 * en los recursos de la app para que el aviso no dependa de que un
 * cliente se acuerde de ponerlo.
 */
public record ComplaintCreatedResponse(
        String codigoSeguimiento,
        boolean anonima,
        Instant creadoEn,
        String avisoImportante
) {
}
