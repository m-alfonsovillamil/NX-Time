package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.ApplicationStatus;
import com.nxtime.nxtime.domain.Attachment;
import com.nxtime.nxtime.domain.AttachmentType;
import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.JobApplication;
import com.nxtime.nxtime.domain.JobPosting;
import com.nxtime.nxtime.domain.JobPostingStatus;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.JobApplicationRequest;
import com.nxtime.nxtime.dto.JobApplicationResponse;
import com.nxtime.nxtime.dto.JobPostingRequest;
import com.nxtime.nxtime.dto.JobPostingResponse;
import com.nxtime.nxtime.dto.UpdateApplicationStatusRequest;
import com.nxtime.nxtime.dto.UpdateJobPostingStatusRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.AttachmentRepository;
import com.nxtime.nxtime.repository.DepartmentRepository;
import com.nxtime.nxtime.repository.JobApplicationRepository;
import com.nxtime.nxtime.repository.JobPostingRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.JobPostingService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ofertas internas y candidaturas (Fase H).
 *
 * <b>La decisión que define la fase está en {@link #presentarCandidatura}:
 * se guarda el ADJUNTO, no el usuario.</b> Si la candidatura apuntara a
 * "el CV de esta persona", subir una versión nueva en marzo cambiaría lo
 * que el gestor leyó en enero y el expediente dejaría de serlo. De ahí
 * sale la deuda que esta fase salda en {@code AttachmentServiceImpl}: un
 * CV congelado deja de estar vigente pero no se destruye.
 *
 * Y como en la fase F, <b>nadie valora su propia candidatura</b>: un
 * GESTOR puede optar a una vacante como cualquiera, y entonces esa
 * candidatura concreta la decide otro.
 */
@Service
@Transactional(readOnly = true)
public class JobPostingServiceImpl implements JobPostingService {

    private static final Logger log = LoggerFactory.getLogger(JobPostingServiceImpl.class);

    private static final String PUBLICAR = "oferta:publicar";
    private static final String GESTIONAR_CANDIDATURAS = "candidatura:gestionar";

    /** El día es el español, como en las jornadas y en los plazos. */
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository applicationRepository;
    private final AttachmentRepository attachmentRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public JobPostingServiceImpl(
            JobPostingRepository jobPostingRepository,
            JobApplicationRepository applicationRepository,
            AttachmentRepository attachmentRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.jobPostingRepository = jobPostingRepository;
        this.applicationRepository = applicationRepository;
        this.attachmentRepository = attachmentRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    // ==================================================================
    // Ofertas
    // ==================================================================

    @Override
    public List<JobPostingResponse> publicadas(User actor) {
        return resumirOfertas(
                jobPostingRepository.findPublicadas(actor.getEmpresa().getId()), actor);
    }

    @Override
    public List<JobPostingResponse> todas(User actor) {
        return resumirOfertas(
                jobPostingRepository.findDeEmpresa(actor.getEmpresa().getId()), actor);
    }

    @Override
    public JobPostingResponse detalle(long id, User actor) {
        JobPosting oferta = deLaMismaEmpresa(id, actor);

        // Un BORRADOR no existe para la plantilla: enseñarlo dejaría ver
        // vacantes a medio escribir, con condiciones que igual cambian
        // antes de publicarse.
        if (!oferta.getEstado().estaPublicada() && !tiene(actor, PUBLICAR)) {
            throw new ResourceNotFoundException("Oferta no encontrada.");
        }
        return toResponse(oferta, actor);
    }

    @Override
    @Transactional
    public JobPostingResponse crear(JobPostingRequest request, User actor) {
        JobPosting oferta = JobPosting.builder()
                .empresa(actor.getEmpresa())
                .publicadaPor(actor)
                // Nace en BORRADOR SIEMPRE. Publicar avisa a toda la
                // plantilla, así que no puede ser el efecto colateral de
                // pulsar "guardar" en un formulario a medio escribir.
                .estado(JobPostingStatus.BORRADOR)
                .creadoEn(Instant.now())
                .build();

        aplicar(request, oferta, actor);
        oferta = jobPostingRepository.save(oferta);

        log.info("{} ha creado la oferta {} ({})", actor.getEmail(), oferta.getId(),
                oferta.getTitulo());
        return toResponse(oferta, actor);
    }

    @Override
    @Transactional
    public JobPostingResponse editar(long id, JobPostingRequest request, User actor) {
        JobPosting oferta = deLaMismaEmpresa(id, actor);
        aplicar(request, oferta, actor);
        jobPostingRepository.save(oferta);

        log.info("{} ha editado la oferta {}", actor.getEmail(), id);
        return toResponse(oferta, actor);
    }

    @Override
    @Transactional
    public JobPostingResponse cambiarEstado(
            long id, UpdateJobPostingStatusRequest request, User actor) {
        JobPosting oferta = deLaMismaEmpresa(id, actor);
        JobPostingStatus destino = request.estado();

        if (oferta.getEstado() == destino) {
            throw new BusinessException("La oferta ya está en ese estado.");
        }
        if (oferta.getEstado() == JobPostingStatus.CERRADA) {
            // Reabrir una vacante cerrada dejaría a quien ya se presentó
            // sin saber si su candidatura sigue contando. Se publica otra.
            throw new BusinessException(
                    "Una oferta cerrada no se reabre: publica una nueva.");
        }

        boolean seEstrena = destino == JobPostingStatus.ABIERTA
                && oferta.getFechaPublicacion() == null;
        if (destino == JobPostingStatus.ABIERTA) {
            oferta.setFechaPublicacion(
                    oferta.getFechaPublicacion() != null ? oferta.getFechaPublicacion()
                            : Instant.now());
        }
        oferta.setEstado(destino);
        jobPostingRepository.save(oferta);

        // El aviso masivo sale UNA vez, la primera que se publica. Sin
        // esto, retirarla al borrador y volver a publicarla avisaría a
        // toda la plantilla otra vez de la misma vacante.
        if (seEstrena) {
            eventPublisher.publishEvent(new NotificationEvents.JobPostingPublished(
                    oferta, laPlantilla(oferta, actor)));
        }

        log.info("{} ha movido la oferta {} a {}", actor.getEmail(), id, destino);
        return toResponse(oferta, actor);
    }

    /** Vuelca el cuerpo sobre la entidad, validando lo que no puede el DTO. */
    private void aplicar(JobPostingRequest request, JobPosting oferta, User actor) {
        oferta.setTitulo(request.titulo().trim());
        oferta.setDescripcion(request.descripcion().trim());
        oferta.setPuesto(vacioANull(request.puesto()));
        oferta.setFechaCierre(request.fechaCierre());
        oferta.setDepartamento(departamentoDe(request.departamentoId(), actor));

        // Una fecha de cierre pasada solo se rechaza si la oferta todavía
        // no está publicada. No es un @Future en el DTO porque al EDITAR
        // una oferta ya publicada su plazo puede ser legítimamente de
        // ayer, y entonces la validación impediría corregirle una errata
        // al título.
        if (oferta.getFechaCierre() != null
                && !oferta.getEstado().estaPublicada()
                && oferta.getFechaCierre().isBefore(LocalDate.now(MADRID))) {
            throw new BusinessException(
                    "La fecha de cierre ya ha pasado.", HttpStatus.BAD_REQUEST);
        }
    }

    private Department departamentoDe(Long departamentoId, User actor) {
        if (departamentoId == null) {
            return null;
        }
        Department departamento = departmentRepository.findById(departamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Departamento no encontrado."));
        if (departamento.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Ese departamento es de otra empresa.");
        }
        return departamento;
    }

    // ==================================================================
    // Candidaturas
    // ==================================================================

    @Override
    @Transactional
    public JobApplicationResponse presentarCandidatura(
            long ofertaId, JobApplicationRequest request, User actor) {
        JobPosting oferta = deLaMismaEmpresa(ofertaId, actor);

        if (!oferta.getEstado().estaPublicada()) {
            throw new BusinessException("Esa oferta no está publicada.");
        }
        if (oferta.plazoVencido()) {
            // Mensaje propio, no un "no admite candidaturas" genérico: no
            // es lo mismo llegar tarde que llegar a una vacante cerrada.
            throw new BusinessException("El plazo para optar a esa oferta ya ha terminado.");
        }

        /*
         * El CV lo pone el SERVIDOR, y es el vigente de quien se
         * presenta. No llega en el cuerpo a propósito: dejarlo elegir
         * obligaría a comprobar que el adjunto es suyo -- una
         * comprobación más que se puede olvidar -- y permitiría adjuntar
         * una versión que ya se retiró.
         */
        Attachment cv = attachmentRepository
                .findByUsuarioAndTipoAndVigenteTrue(actor, AttachmentType.CV)
                .orElseThrow(() -> new BusinessException(
                        "Necesitas tener un CV subido en tu perfil para presentarte.",
                        HttpStatus.BAD_REQUEST));

        JobApplication candidatura = JobApplication.builder()
                .oferta(oferta)
                .usuario(actor)
                // El adjunto CONCRETO, congelado aquí. A partir de este
                // momento ese CV ya no se puede destruir.
                .cv(cv)
                .carta(vacioANull(request.carta()))
                .estado(ApplicationStatus.RECIBIDA)
                .creadoEn(Instant.now())
                .build();

        candidatura = guardarControlandoLaCarrera(candidatura);

        eventPublisher.publishEvent(new NotificationEvents.JobApplicationReceived(
                candidatura, List.of(oferta.getPublicadaPor())));

        log.info("{} se ha presentado a la oferta {} con el adjunto {}",
                actor.getEmail(), ofertaId, cv.getId());
        return toResponse(candidatura, actor);
    }

    @Override
    public List<JobApplicationResponse> candidaturasDeOferta(long ofertaId, User actor) {
        deLaMismaEmpresa(ofertaId, actor);
        return applicationRepository.findDeOferta(ofertaId).stream()
                .map(candidatura -> toResponse(candidatura, actor))
                .toList();
    }

    @Override
    public List<JobApplicationResponse> misCandidaturas(User actor) {
        return applicationRepository.findMias(actor.getId()).stream()
                .map(candidatura -> toResponse(candidatura, actor))
                .toList();
    }

    @Override
    @Transactional
    public JobApplicationResponse valorar(
            long candidaturaId, UpdateApplicationStatusRequest request, User actor) {
        JobApplication candidatura = applicationRepository.findById(candidaturaId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura no encontrada."));

        if (candidatura.getOferta().getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Esa candidatura es de otra empresa.");
        }
        if (!puedeValorar(candidatura, actor)) {
            // Un GESTOR puede optar a una vacante como cualquiera; lo que
            // no puede es decidir sobre lo suyo. Como en la fase F, esto
            // no lo puede parar un @PreAuthorize: tiene la authority.
            throw new BusinessException(
                    "No puedes valorar tu propia candidatura.", HttpStatus.FORBIDDEN);
        }

        ApplicationStatus destino = request.estado();
        boolean hayComentario = request.comentario() != null && !request.comentario().isBlank();

        if (candidatura.getEstado() == destino) {
            throw new BusinessException("La candidatura ya está en ese estado.");
        }
        if (candidatura.getEstado().esFinal()) {
            throw new BusinessException("Esa candidatura ya está resuelta.");
        }
        if (destino == ApplicationStatus.DESCARTADA && !hayComentario) {
            throw new BusinessException(
                    "Al descartar una candidatura hay que explicar por qué.",
                    HttpStatus.BAD_REQUEST);
        }

        candidatura.setEstado(destino);
        candidatura.setResueltaPor(actor);
        candidatura.setFechaResolucion(Instant.now());
        candidatura.setComentario(hayComentario ? request.comentario().trim() : null);
        applicationRepository.save(candidatura);

        eventPublisher.publishEvent(new NotificationEvents.JobApplicationUpdated(
                candidatura, List.of(candidatura.getUsuario())));

        log.info("{} ha movido la candidatura {} a {}", actor.getEmail(), candidaturaId, destino);
        return toResponse(candidatura, actor);
    }

    /**
     * Si a {@code actor} le toca decidir sobre esta candidatura.
     *
     * Es la única definición de la regla, y viaja resuelta al cliente en
     * {@code puedoValorar} para que la app no la reimplemente.
     */
    private boolean puedeValorar(JobApplication candidatura, User actor) {
        return tiene(actor, GESTIONAR_CANDIDATURAS)
                && candidatura.getUsuario().getId() != actor.getId();
    }

    /**
     * Guarda la candidatura traduciendo el choque del UNIQUE.
     *
     * Dos pulsaciones seguidas del botón pasan las dos cualquier
     * comprobación previa; la segunda la para la base. Sin esto llegaría
     * al cliente como un 500.
     */
    private JobApplication guardarControlandoLaCarrera(JobApplication candidatura) {
        try {
            return applicationRepository.saveAndFlush(candidatura);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Ya te has presentado a esta oferta.");
        }
    }

    // ==================================================================

    /**
     * A quién avisar de una vacante nueva: a toda la empresa menos a
     * quien la publica.
     *
     * Aquí SÍ se excluye al autor, al revés que en las denuncias de la
     * fase G: quién publica una oferta es público, así que su ausencia
     * de la lista no revela nada.
     */
    private List<User> laPlantilla(JobPosting oferta, User autor) {
        List<User> destinatarios = new ArrayList<>();
        for (User candidato : userRepository.findByEmpresa(oferta.getEmpresa())) {
            if (candidato.isActivo() && candidato.getId() != autor.getId()) {
                destinatarios.add(candidato);
            }
        }
        return destinatarios;
    }

    private JobPosting deLaMismaEmpresa(long id, User actor) {
        JobPosting oferta = jobPostingRepository.findConDetalle(id)
                .orElseThrow(() -> new ResourceNotFoundException("Oferta no encontrada."));
        if (oferta.getEmpresa().getId() != actor.getEmpresa().getId()) {
            throw new TenantAccessException("Esa oferta es de otra empresa.");
        }
        return oferta;
    }

    private boolean tiene(User usuario, String authority) {
        return RoleAuthorities.forRole(usuario.getRol()).contains(authority);
    }

    private String vacioANull(String valor) {
        return valor != null && !valor.isBlank() ? valor.trim() : null;
    }

    // ==================================================================
    // A DTO
    // ==================================================================

    /**
     * Las ofertas de una lista, contando sus candidaturas en <b>una</b>
     * consulta.
     */
    private List<JobPostingResponse> resumirOfertas(List<JobPosting> ofertas, User actor) {
        if (ofertas.isEmpty()) {
            // Ni una consulta más: además, un IN vacío no es SQL válido.
            return List.of();
        }
        boolean valora = tiene(actor, GESTIONAR_CANDIDATURAS);
        Map<Long, Long> candidaturas = valora ? contarCandidaturas(ofertas) : Map.of();

        // Las mías, de una vez. Preguntar "¿me he presentado a esta?"
        // oferta a oferta serían N consultas más en la pantalla que más
        // filas tiene, y son siempre pocas: las de una persona.
        Set<Long> yaPresentadas = applicationRepository.findMias(actor.getId()).stream()
                .map(candidatura -> candidatura.getOferta().getId())
                .collect(Collectors.toSet());

        return ofertas.stream()
                .map(oferta -> toResponse(
                        oferta, actor,
                        valora ? candidaturas.getOrDefault(oferta.getId(), 0L) : null,
                        yaPresentadas.contains(oferta.getId())))
                .toList();
    }

    private Map<Long, Long> contarCandidaturas(List<JobPosting> ofertas) {
        List<Long> ids = ofertas.stream().map(JobPosting::getId).toList();
        Map<Long, Long> porOferta = new HashMap<>();
        for (Object[] fila : applicationRepository.contarPorOferta(ids)) {
            // Number y no un cast directo: el tipo exacto de un COUNT
            // depende del dialecto, y un ClassCastException aquí solo
            // aparecería en producción con datos reales.
            porOferta.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }
        return porOferta;
    }

    /**
     * Una oferta suelta: se resuelven sus dos datos derivados aquí
     * mismo. Dos consultas para una fila es aceptable; para una lista no
     * lo sería, y por eso {@link #resumirOfertas} los trae en bloque.
     */
    private JobPostingResponse toResponse(JobPosting oferta, User actor) {
        Long candidaturas = tiene(actor, GESTIONAR_CANDIDATURAS)
                ? (long) applicationRepository.findDeOferta(oferta.getId()).size()
                : null;
        boolean yaMePresente = applicationRepository
                .findByOferta_IdAndUsuario_Id(oferta.getId(), actor.getId())
                .isPresent();
        return toResponse(oferta, actor, candidaturas, yaMePresente);
    }

    /**
     * @param candidaturas cuántas tiene, o null si quien mira no puede
     *                     valorarlas. Llega desde fuera para que listar
     *                     N ofertas no dispare N consultas.
     */
    private JobPostingResponse toResponse(
            JobPosting oferta, User actor, Long candidaturas, boolean yaMePresente) {
        return new JobPostingResponse(
                oferta.getId(),
                oferta.getTitulo(),
                oferta.getDescripcion(),
                oferta.getPuesto(),
                oferta.getDepartamento() != null ? oferta.getDepartamento().getNombre() : null,
                oferta.getPublicadaPor().getNombre(),
                oferta.getEstado(),
                oferta.getFechaPublicacion(),
                oferta.getFechaCierre(),
                oferta.admiteCandidaturas(),
                oferta.plazoVencido(),
                yaMePresente,
                tiene(actor, GESTIONAR_CANDIDATURAS) ? candidaturas : null);
    }

    private JobApplicationResponse toResponse(JobApplication candidatura, User actor) {
        return new JobApplicationResponse(
                candidatura.getId(),
                candidatura.getOferta().getId(),
                candidatura.getOferta().getTitulo(),
                candidatura.getUsuario().getId(),
                candidatura.getUsuario().getNombre(),
                candidatura.getCarta(),
                candidatura.getEstado(),
                candidatura.getResueltaPor() != null
                        ? candidatura.getResueltaPor().getNombre() : null,
                candidatura.getFechaResolucion(),
                candidatura.getComentario(),
                candidatura.getCreadoEn(),
                candidatura.getCv().getId(),
                candidatura.getCv().getNombreOriginal(),
                puedeValorar(candidatura, actor));
    }
}
