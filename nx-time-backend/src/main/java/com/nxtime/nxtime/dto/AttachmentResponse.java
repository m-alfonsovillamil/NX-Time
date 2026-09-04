package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.AttachmentType;
import java.time.Instant;

/**
 * Un adjunto, SIN su contenido.
 *
 * Los bytes se piden aparte, en su propio endpoint y en streaming: si
 * viajaran aquí, listar los adjuntos de alguien descargaría su
 * currículum entero para enseñar un nombre y una fecha.
 *
 * @param mime el real, deducido de los primeros bytes al subir. En una
 *     foto es siempre {@code image/jpeg}, porque el servidor la
 *     reescala a JPEG sea cual sea el original.
 * @param tamanoBytes el de lo GUARDADO, que en una foto no es el del
 *     fichero que se subió.
 */
public record AttachmentResponse(
        long id,
        AttachmentType tipo,
        String nombreOriginal,
        String mime,
        long tamanoBytes,
        Instant subidoEn
) {
}
