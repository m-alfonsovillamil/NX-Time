package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.JobApplicationRequest;
import com.nxtime.nxtime.dto.JobApplicationResponse;
import com.nxtime.nxtime.dto.JobPostingRequest;
import com.nxtime.nxtime.dto.JobPostingResponse;
import com.nxtime.nxtime.dto.UpdateApplicationStatusRequest;
import com.nxtime.nxtime.dto.UpdateJobPostingStatusRequest;
import java.util.List;

/**
 * Ofertas internas y candidaturas (Fase H).
 *
 * Dos reglas ordenan el servicio entero:
 *
 * <ul>
 *   <li><b>La candidatura congela el CV.</b> Se guarda el adjunto
 *       concreto que había al presentarse, no una referencia a "el CV de
 *       esta persona". Por eso subir uno nuevo ya no borra el anterior
 *       cuando alguien lo ha congelado (ver {@code AttachmentService} y
 *       la V14).</li>
 *   <li><b>Nadie valora su propia candidatura</b>, tenga el rol que
 *       tenga. Es la misma regla que la fase F aplicó a las horas extra,
 *       y como allí no la puede poner un {@code @PreAuthorize}: quien
 *       valora tiene la authority; lo que falla es que el expediente sea
 *       suyo.</li>
 * </ul>
 */
public interface JobPostingService {

    /* ---- Ofertas ---- */

    /** Las vacantes publicadas de mi empresa. */
    List<JobPostingResponse> publicadas(User actor);

    /** Todas las de la empresa, borradores incluidos. Para quien publica. */
    List<JobPostingResponse> todas(User actor);

    /** Una oferta concreta. Un borrador solo lo ve quien puede publicar. */
    JobPostingResponse detalle(long id, User actor);

    /** Crear una oferta. Nace en BORRADOR: publicar es un gesto aparte. */
    JobPostingResponse crear(JobPostingRequest request, User actor);

    /** Editar el contenido. No toca el estado. */
    JobPostingResponse editar(long id, JobPostingRequest request, User actor);

    /** Publicarla, retirarla al borrador o cerrarla. */
    JobPostingResponse cambiarEstado(long id, UpdateJobPostingStatusRequest request, User actor);

    /* ---- Candidaturas ---- */

    /** Presentarse. Adjunta el CV vigente de quien se presenta. */
    JobApplicationResponse presentarCandidatura(
            long ofertaId, JobApplicationRequest request, User actor);

    /** Las candidaturas de una oferta, para quien las valora. */
    List<JobApplicationResponse> candidaturasDeOferta(long ofertaId, User actor);

    /** Las que he presentado yo. */
    List<JobApplicationResponse> misCandidaturas(User actor);

    /** Mover una candidatura de estado. Descartar exige comentario. */
    JobApplicationResponse valorar(
            long candidaturaId, UpdateApplicationStatusRequest request, User actor);
}
