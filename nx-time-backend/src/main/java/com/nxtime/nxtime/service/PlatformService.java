package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.PlatformOrder;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PaginaDTO;
import com.nxtime.nxtime.dto.PlatformCompanyDetailResponse;
import com.nxtime.nxtime.dto.PlatformCompanyResponse;
import com.nxtime.nxtime.dto.PlatformSummaryResponse;
import org.springframework.data.domain.Pageable;

/**
 * El panel de plataforma (ADR 040): la instalación entera, para quien la
 * mantiene. Solo lectura.
 *
 * <b>Es el único servicio que no recorta por la empresa de quien pregunta</b>,
 * y por eso ninguno de sus métodos la recibe: quien llega aquí ya ha pasado
 * por {@code plataforma:ver}, que no es de ningún rol.
 */
public interface PlatformService {

    /** Los totales, las tareas nocturnas y el estado de la traza de auditoría. */
    PlatformSummaryResponse resumen();

    /**
     * Las empresas dadas de alta, con sus cifras de uso.
     *
     * @param busqueda parte del nombre, sin distinguir mayúsculas; null o vacía, todas
     */
    PaginaDTO<PlatformCompanyResponse> empresas(String busqueda, PlatformOrder orden, Pageable pagina);

    /**
     * Una empresa. Deja apuntado en el log quién la ha mirado.
     *
     * @throws com.nxtime.nxtime.exception.ResourceNotFoundException si no existe
     */
    PlatformCompanyDetailResponse empresa(long empresaId, User operador);
}
