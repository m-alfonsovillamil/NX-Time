package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.AttachmentType;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AttachmentResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * El CV y la foto de perfil (Fase B2). Ver ADR 007.
 *
 * Es la única clase que sabe dónde viven los bytes: el día que dejen de
 * caber en PostgreSQL, lo que cambia es el cuerpo de su implementación.
 */
public interface AttachmentService {

    /**
     * Guarda un adjunto, reemplazando el que hubiera de ese tipo.
     *
     * Valida el contenido por sus primeros bytes, no por la extensión ni
     * por el Content-Type declarado. Una FOTO se reescala a 256x256 JPEG
     * antes de guardarse.
     *
     * @throws com.nxtime.nxtime.exception.BusinessException 400 si el contenido no es del tipo esperado
     */
    AttachmentResponse subir(MultipartFile fichero, AttachmentType tipo, User actor);

    /** Los adjuntos de una persona, sin contenido. */
    List<AttachmentResponse> listar(User usuario);

    /**
     * Los bytes, para descargarlos. Es la ÚNICA operación que los lee.
     *
     * @throws com.nxtime.nxtime.exception.ResourceNotFoundException si no existe
     * @throws com.nxtime.nxtime.exception.TenantAccessException si es de otra empresa
     */
    ContenidoDeAdjunto descargar(long adjuntoId, User actor);

    /**
     * @throws com.nxtime.nxtime.exception.TenantAccessException si el adjunto no es del actor
     */
    void borrar(long adjuntoId, User actor);

    /** Los bytes junto a lo que hace falta para servirlos por HTTP. */
    record ContenidoDeAdjunto(byte[] contenido, String nombreOriginal, String mime, AttachmentType tipo) {
    }
}
