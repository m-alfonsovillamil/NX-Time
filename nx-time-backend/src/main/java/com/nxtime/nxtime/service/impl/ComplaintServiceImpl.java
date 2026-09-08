package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Complaint;
import com.nxtime.nxtime.domain.ComplaintAuthor;
import com.nxtime.nxtime.domain.ComplaintMessage;
import com.nxtime.nxtime.domain.ComplaintStatus;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ComplaintCreatedResponse;
import com.nxtime.nxtime.dto.ComplaintMessageRequest;
import com.nxtime.nxtime.dto.ComplaintMessageResponse;
import com.nxtime.nxtime.dto.ComplaintResponse;
import com.nxtime.nxtime.dto.ComplaintSummaryResponse;
import com.nxtime.nxtime.dto.CreateComplaintRequest;
import com.nxtime.nxtime.dto.UpdateComplaintStatusRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.ComplaintMessageRepository;
import com.nxtime.nxtime.repository.ComplaintRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.ComplaintService;
import com.nxtime.nxtime.service.TrackingCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El canal de denuncias (Fase G).
 *
 * <b>Toda la clase está escrita alrededor de una sola pregunta: dónde
 * podría escaparse la identidad de quien denunció.</b> No es una
 * preocupación abstracta — se escapa por sitios que en cualquier otra
 * fase serían buenas prácticas:
 *
 * <ul>
 *   <li><b>El log.</b> Un {@code log.info("{} ha presentado una
 *       denuncia", actor.getEmail())} es exactamente lo que se escribe
 *       en las otras siete fases, y aquí deja el nombre del denunciante
 *       anónimo en un fichero que lee media empresa. Ver
 *       {@link #presentar}.</li>
 *   <li><b>Los destinatarios de un aviso.</b> Excluir al autor de la
 *       lista de avisados — otra cortesía habitual — permite deducir
 *       quién es por quién NO recibió el aviso. Ver
 *       {@link #quienInstruye}.</li>
 *   <li><b>El autor de un mensaje.</b> Guardar el id de quien escribe
 *       "por trazabilidad" desharía el anonimato en el primer mensaje de
 *       seguimiento. Ver {@link #responder}.</li>
 *   <li><b>El código.</b> Solo se guarda su hash, así que no hay forma
 *       de reenviarlo. Ver {@link com.nxtime.nxtime.service.TrackingCode}.</li>
 * </ul>
 *
 * Los plazos del art. 9.2 (acuse en 7 días naturales, respuesta en 3
 * meses) se CALCULAN al leer y no se guardan: un plazo guardado nace
 * caducado.
 */
@Service
@Transactional(readOnly = true)
public class ComplaintServiceImpl implements ComplaintService {

    private static final Logger log = LoggerFactory.getLogger(ComplaintServiceImpl.class);

    /** Quien instruye. Solo ADMIN (ver {@link RoleAuthorities}). */
    private static final String INSTRUIR = "denuncia:instruir";

    /** Art. 9.2 Ley 2/2023: acuse de recibo en 7 días naturales. */
    private static final int DIAS_PARA_ACUSAR = 7;

    /** Art. 9.2 Ley 2/2023: respuesta en 3 meses. */
    private static final int MESES_PARA_RESPONDER = 3;

    /**
     * Los plazos son en días NATURALES y se cuentan en el calendario de
     * aquí, no en UTC: una denuncia presentada el día 1 a las 00:30 hora
     * española es del 31 en UTC, y el plazo empezaría a contar un día
     * antes. Es el mismo cuidado que la fase D tuvo con las jornadas.
     */
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final ComplaintRepository complaintRepository;
    private final ComplaintMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ComplaintServiceImpl(
            ComplaintRepository complaintRepository,
            ComplaintMessageRepository messageRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.complaintRepository = complaintRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    // ------------------------------------------------------------------
    // Presentar
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public ComplaintCreatedResponse presentar(CreateComplaintRequest request, User actor) {
        boolean anonima = Boolean.TRUE.equals(request.anonima());

        String codigo = TrackingCode.generar();
        Complaint denuncia = complaintRepository.save(Complaint.builder()
                .empresa(actor.getEmpresa())
                .codigoHash(TrackingCode.hash(codigo))
                // Aquí está la decisión entera: si es anónima, el id del
                // actor -- que tenemos delante, en el token -- se
                // descarta y no se guarda en ninguna parte.
                .denunciante(anonima ? null : actor)
                .categoria(request.categoria())
                .descripcion(request.descripcion().trim())
                .estado(ComplaintStatus.RECIBIDA)
                .creadoEn(Instant.now())
                .build());

        // El log dice QUE ha entrado una denuncia y de qué categoría,
        // nunca de quién. En cualquier otra fase de este proyecto la
        // línea llevaría actor.getEmail(); aquí eso sería guardar en un
        // fichero de texto lo que la base de datos se ha cuidado de no
        // guardar.
        log.info("Denuncia {} presentada en la empresa {} (categoría {}, anónima: {})",
                denuncia.getId(), actor.getEmpresa().getId(), denuncia.getCategoria(), anonima);

        eventPublisher.publishEvent(new NotificationEvents.ComplaintReceived(
                denuncia, quienInstruye(actor.getEmpresa())));

        return new ComplaintCreatedResponse(
                codigo,
                anonima,
                denuncia.getCreadoEn(),
                "Guarda este código: es la única forma de seguir tu denuncia y de "
                        + "responder a quien la instruya. No se puede recuperar ni volver a "
                        + "enviar, porque poder hacerlo sería poder saber que la denuncia "
                        + "es tuya.");
    }

    // ------------------------------------------------------------------
    // La puerta del denunciante: el código
    // ------------------------------------------------------------------

    @Override
    public ComplaintResponse seguimiento(String codigo, User actor) {
        return toResponse(porCodigo(codigo, actor));
    }

    @Override
    @Transactional
    public ComplaintResponse responder(String codigo, ComplaintMessageRequest request, User actor) {
        Complaint denuncia = porCodigo(codigo, actor);
        exigirExpedienteAbierto(denuncia);

        /*
         * El autor va a null cuando la denuncia es anónima, AUNQUE
         * tengamos a 'actor' delante y esté perfectamente autenticado.
         *
         * Es el punto donde más fácil sería equivocarse con buena
         * intención: guardar el id "para trazar quién escribió" parece
         * inofensivo -- ya sabemos quién es, nos ha mandado un token --
         * y sin embargo ata al denunciante con su denuncia en el primer
         * mensaje de seguimiento. El anonimato de la denuncia no serviría
         * de nada si la conversación lo deshace.
         */
        anotarMensaje(denuncia, ComplaintAuthor.DENUNCIANTE,
                denuncia.esAnonima() ? null : actor, request.texto());

        eventPublisher.publishEvent(new NotificationEvents.ComplaintUpdated(
                denuncia,
                quienInstruye(denuncia.getEmpresa()),
                "Hay un mensaje nuevo del denunciante."));

        log.info("Mensaje del denunciante en la denuncia {}", denuncia.getId());
        return toResponse(denuncia);
    }

    @Override
    public List<ComplaintSummaryResponse> mias(User actor) {
        return resumir(complaintRepository.findMias(actor.getId()));
    }

    @Override
    public ComplaintResponse miDetalle(long id, User actor) {
        return toResponse(miaPorId(id, actor));
    }

    @Override
    @Transactional
    public ComplaintResponse responderComoDenunciante(
            long id, ComplaintMessageRequest request, User actor) {
        Complaint denuncia = miaPorId(id, actor);
        exigirExpedienteAbierto(denuncia);

        // Aquí el autor SÍ va: por definición esta puerta solo la abre
        // una denuncia identificada, y en esas el nombre de quien
        // escribe ya era público dentro del expediente.
        anotarMensaje(denuncia, ComplaintAuthor.DENUNCIANTE, actor, request.texto());

        eventPublisher.publishEvent(new NotificationEvents.ComplaintUpdated(
                denuncia,
                quienInstruye(denuncia.getEmpresa()),
                "Hay un mensaje nuevo del denunciante."));

        log.info("Mensaje del denunciante en la denuncia {}", denuncia.getId());
        return toResponse(denuncia);
    }

    /**
     * Un expediente propio, por id.
     *
     * Exige que la denuncia sea de esta empresa Y que la presentara este
     * usuario identificándose. Sobre una <b>anónima devuelve 404 aunque
     * sea suya</b>, y no es una carencia: el sistema no sabe que lo es,
     * que es justo lo que se prometió. Para esas está el código.
     */
    private Complaint miaPorId(long id, User actor) {
        return complaintRepository.findById(id)
                .filter(d -> d.getEmpresa().getId() == actor.getEmpresa().getId())
                .filter(d -> !d.esAnonima() && d.getDenunciante().getId() == actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Denuncia no encontrada."));
    }

    // ------------------------------------------------------------------
    // La puerta del instructor: el id y la authority
    // ------------------------------------------------------------------

    @Override
    public List<ComplaintSummaryResponse> bandeja(User actor) {
        return resumir(complaintRepository.findDeEmpresa(actor.getEmpresa().getId()));
    }

    @Override
    public ComplaintResponse detalle(long id, User actor) {
        return toResponse(porId(id, actor));
    }

    @Override
    @Transactional
    public ComplaintResponse responderComoInstructor(
            long id, ComplaintMessageRequest request, User actor) {
        Complaint denuncia = porId(id, actor);
        exigirQueNoSeaSuya(denuncia, actor);
        exigirExpedienteAbierto(denuncia);

        // El instructor NUNCA es anónimo: responde de lo que escribe.
        anotarMensaje(denuncia, ComplaintAuthor.INSTRUCTOR, actor, request.texto());
        acusarRecibo(denuncia);

        avisarAlDenunciante(denuncia, "Han respondido en tu denuncia.");

        log.info("{} ha escrito en la denuncia {}", actor.getEmail(), denuncia.getId());
        return toResponse(denuncia);
    }

    @Override
    @Transactional
    public ComplaintResponse cambiarEstado(
            long id, UpdateComplaintStatusRequest request, User actor) {
        Complaint denuncia = porId(id, actor);
        exigirQueNoSeaSuya(denuncia, actor);

        ComplaintStatus destino = request.estado();
        boolean cierra = !destino.estaAbierta();
        boolean hayConclusion = request.conclusion() != null && !request.conclusion().isBlank();

        if (!denuncia.getEstado().estaAbierta()) {
            throw new BusinessException("Ese expediente ya está cerrado.");
        }
        if (denuncia.getEstado() == destino) {
            throw new BusinessException("La denuncia ya está en ese estado.");
        }
        if (cierra && !hayConclusion) {
            // La ley obliga a RESPONDER, no a dar la razón: archivar una
            // denuncia que no se sostiene también hay que explicarlo.
            throw new BusinessException(
                    "Al cerrar una denuncia hay que escribir la conclusión.",
                    HttpStatus.BAD_REQUEST);
        }
        if (!cierra && hayConclusion) {
            // No es quisquillosidad: la conclusión de un expediente
            // abierto la prohíbe ck_denuncias_cierre_coherente, y sin
            // esta comprobación el INSERT saldría como un 500.
            throw new BusinessException(
                    "La conclusión solo se escribe al cerrar el expediente.",
                    HttpStatus.BAD_REQUEST);
        }

        // Antes del cambio: si se cierra sin haber pasado por
        // EN_INVESTIGACION, el acuse se fecha aquí y ahora. Sin esto,
        // ck_denuncias_acuse_antes_del_cierre pararía el UPDATE.
        acusarRecibo(denuncia);

        denuncia.setEstado(destino);
        if (cierra) {
            denuncia.setResueltaEn(Instant.now());
            denuncia.setConclusion(request.conclusion().trim());
        }
        complaintRepository.save(denuncia);

        avisarAlDenunciante(denuncia, cierra
                ? "Tu denuncia se ha cerrado y ya tiene conclusión."
                : "Tu denuncia ha pasado a estar en investigación.");

        log.info("{} ha movido la denuncia {} a {}", actor.getEmail(), denuncia.getId(), destino);
        return toResponse(denuncia);
    }

    // ------------------------------------------------------------------
    // Buscar el expediente por cada una de las dos puertas
    // ------------------------------------------------------------------

    /**
     * El expediente al que abre un código.
     *
     * <b>No se comprueba quién lo trae</b>, y esa es la razón de ser del
     * código: en una denuncia anónima no hay contra quién comprobarlo.
     * Sí se comprueba la empresa, como en todo el proyecto (ADR 006).
     *
     * Un código que no existe y uno de otra empresa dan el <b>mismo</b>
     * 404. Distinguirlos con un 403 confirmaría que ese código es válido
     * en algún sitio, que es la mitad de lo que necesita quien va
     * probando.
     */
    private Complaint porCodigo(String codigo, User actor) {
        if (codigo == null || codigo.isBlank()) {
            throw new ResourceNotFoundException("No hay ninguna denuncia con ese código.");
        }
        return complaintRepository.findByCodigoHash(TrackingCode.hash(codigo))
                .filter(d -> d.getEmpresa().getId() == actor.getEmpresa().getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("No hay ninguna denuncia con ese código."));
    }

    /** El expediente por id, para quien instruye. Mismo criterio con el 404. */
    private Complaint porId(long id, User actor) {
        return complaintRepository.findById(id)
                .filter(d -> d.getEmpresa().getId() == actor.getEmpresa().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Denuncia no encontrada."));
    }

    // ------------------------------------------------------------------

    /**
     * Nadie instruye su propia denuncia.
     *
     * Es la misma regla que la fase F aplica a las horas extra y por el
     * mismo motivo, y tampoco aquí puede ponerla un {@code @PreAuthorize}:
     * quien instruye tiene la authority; lo que falla no es el permiso
     * sino que el expediente sea suyo.
     *
     * <b>Solo puede aplicarse a las identificadas</b>, porque de una
     * anónima el sistema no sabe de quién es — y no poder aplicarla es
     * el precio del anonimato, no un agujero. La asimetría no delata
     * nada: que una denuncia sea anónima ya viaja en el expediente.
     *
     * Si quien instruye es el único ADMIN de la empresa, su propia
     * denuncia se queda sin nadie que la tramite. Es la consecuencia
     * aceptada: la salida correcta es designar a otra persona o
     * externalizar el canal, no dejar que se instruya a sí mismo. Ver
     * ADR 012.
     */
    private void exigirQueNoSeaSuya(Complaint denuncia, User actor) {
        if (!denuncia.esAnonima() && denuncia.getDenunciante().getId() == actor.getId()) {
            throw new BusinessException(
                    "No puedes instruir una denuncia que has presentado tú.", HttpStatus.FORBIDDEN);
        }
    }

    private void exigirExpedienteAbierto(Complaint denuncia) {
        if (!denuncia.getEstado().estaAbierta()) {
            throw new BusinessException(
                    "Ese expediente está cerrado y ya no admite mensajes.");
        }
    }

    private void anotarMensaje(
            Complaint denuncia, ComplaintAuthor rol, User autor, String texto) {
        messageRepository.save(ComplaintMessage.builder()
                .denuncia(denuncia)
                .autorRol(rol)
                .autor(autor)
                .texto(texto.trim())
                .creadoEn(Instant.now())
                .build());
    }

    /**
     * Fecha el acuse de recibo la primera vez que quien instruye toca el
     * expediente, sea con un mensaje o con un cambio de estado.
     *
     * Se cuenta el primer contacto REAL y no la creación ni una lectura:
     * lo que el art. 9.2 pide acusar es que alguien se ha hecho cargo, y
     * abrir la pantalla no es hacerse cargo de nada.
     */
    private void acusarRecibo(Complaint denuncia) {
        if (denuncia.getAcuseReciboEn() == null) {
            denuncia.setAcuseReciboEn(Instant.now());
            complaintRepository.save(denuncia);
        }
    }

    /**
     * Avisa a quien denunció, <b>si se puede</b>.
     *
     * En una denuncia anónima no hay a quién avisar: no hay destinatario
     * para un aviso in-app ni dirección a la que escribir. No es una
     * limitación que arreglar sino la contrapartida exacta del
     * anonimato, y por eso la app le dice desde el principio que tendrá
     * que volver con su código.
     */
    private void avisarAlDenunciante(Complaint denuncia, String novedad) {
        if (denuncia.esAnonima()) {
            return;
        }
        eventPublisher.publishEvent(new NotificationEvents.ComplaintUpdated(
                denuncia, List.of(denuncia.getDenunciante()), novedad));
    }

    /**
     * A quién avisar de una denuncia nueva: a quien puede instruirla.
     *
     * <b>No se excluye a nadie</b>, ni siquiera a quien acaba de
     * presentarla. En el resto del proyecto excluir al autor es una
     * cortesía (evita avisarte de lo que acabas de hacer); aquí sería un
     * canal encubierto: con dos personas que instruyen, si una no recibe
     * el aviso, la otra sabe quién ha denunciado. La lista de avisados
     * no puede depender de quién es el denunciante, y la forma segura de
     * garantizarlo es que no lo mire.
     */
    private List<User> quienInstruye(Company empresa) {
        List<User> instructores = new ArrayList<>();
        for (User candidato : userRepository.findByEmpresa(empresa)) {
            if (candidato.isActivo()
                    && RoleAuthorities.forRole(candidato.getRol()).contains(INSTRUIR)) {
                instructores.add(candidato);
            }
        }
        return instructores;
    }

    // ------------------------------------------------------------------
    // Plazos legales, calculados
    // ------------------------------------------------------------------

    /**
     * Días naturales que quedan para acusar recibo; negativo si el plazo
     * ya se pasó, null si el acuse ya se dio.
     *
     * Se devuelve el número negativo en vez de un cero o un booleano
     * "vencido" porque "hace once días que tenía que haberse acusado" es
     * información que quien instruye necesita ver, y un plazo incumplido
     * no se arregla dejando de contarlo.
     */
    private Long diasHastaAcuse(Complaint denuncia) {
        if (denuncia.getAcuseReciboEn() != null) {
            return null;
        }
        return diasHasta(diaEspanol(denuncia.getCreadoEn()).plusDays(DIAS_PARA_ACUSAR));
    }

    /** Igual, con los 3 meses de la respuesta. Null si ya está cerrada. */
    private Long diasHastaRespuesta(Complaint denuncia) {
        if (!denuncia.getEstado().estaAbierta()) {
            return null;
        }
        return diasHasta(diaEspanol(denuncia.getCreadoEn()).plusMonths(MESES_PARA_RESPONDER));
    }

    /**
     * Días completos entre hoy y una fecha límite, en el calendario de
     * aquí.
     *
     * Se cuenta sobre fechas y no sobre instantes a propósito: un plazo
     * en días naturales se mide en el calendario, así que "quedan 2
     * días" tiene que decir lo mismo a las 9:00 que a las 23:00 del
     * mismo día. Restando instantes, ese mismo plazo pasaría de 2 a 1 a
     * media tarde sin que hubiera cambiado el día.
     */
    private long diasHasta(LocalDate limite) {
        return ChronoUnit.DAYS.between(LocalDate.now(MADRID), limite);
    }

    private LocalDate diaEspanol(Instant instante) {
        return instante.atZone(MADRID).toLocalDate();
    }

    // ------------------------------------------------------------------
    // A DTO
    // ------------------------------------------------------------------

    private ComplaintResponse toResponse(Complaint denuncia) {
        return new ComplaintResponse(
                denuncia.getId(),
                denuncia.getCategoria(),
                denuncia.getCategoria().getEtiqueta(),
                denuncia.getDescripcion(),
                denuncia.getEstado(),
                denuncia.esAnonima(),
                denuncia.esAnonima() ? null : denuncia.getDenunciante().getNombre(),
                denuncia.getCreadoEn(),
                denuncia.getAcuseReciboEn(),
                denuncia.getResueltaEn(),
                denuncia.getConclusion(),
                diasHastaAcuse(denuncia),
                diasHastaRespuesta(denuncia),
                mensajesDe(denuncia));
    }

    private List<ComplaintMessageResponse> mensajesDe(Complaint denuncia) {
        return messageRepository.findDeDenuncia(denuncia.getId()).stream()
                .map(mensaje -> new ComplaintMessageResponse(
                        mensaje.getId(),
                        mensaje.getAutorRol(),
                        mensaje.getAutor() != null ? mensaje.getAutor().getNombre() : null,
                        mensaje.getTexto(),
                        mensaje.getCreadoEn()))
                .toList();
    }

    /**
     * Los resúmenes de una lista de denuncias, contando sus mensajes en
     * <b>una</b> consulta en vez de una por fila.
     */
    private List<ComplaintSummaryResponse> resumir(List<Complaint> denuncias) {
        if (denuncias.isEmpty()) {
            // Ni una consulta más: además, un IN vacío no es SQL válido.
            return List.of();
        }
        Map<Long, Integer> mensajes = contarMensajes(denuncias);
        return denuncias.stream()
                .map(denuncia -> new ComplaintSummaryResponse(
                        denuncia.getId(),
                        denuncia.getCategoria(),
                        denuncia.getCategoria().getEtiqueta(),
                        denuncia.getEstado(),
                        denuncia.esAnonima(),
                        denuncia.getCreadoEn(),
                        denuncia.getAcuseReciboEn(),
                        diasHastaAcuse(denuncia),
                        diasHastaRespuesta(denuncia),
                        mensajes.getOrDefault(denuncia.getId(), 0)))
                .toList();
    }

    private Map<Long, Integer> contarMensajes(List<Complaint> denuncias) {
        List<Long> ids = denuncias.stream().map(Complaint::getId).toList();
        Map<Long, Integer> porDenuncia = new HashMap<>();
        for (Object[] fila : messageRepository.contarPorDenuncia(ids)) {
            // Number y no un cast a Long/Integer directo: el tipo exacto
            // que devuelve un GROUP BY depende del dialecto, y aquí un
            // ClassCastException solo aparecería en producción con datos
            // reales -- la bandeja vacía de un test no pasa por aquí.
            porDenuncia.put(((Number) fila[0]).longValue(), ((Number) fila[1]).intValue());
        }
        return porDenuncia;
    }
}
