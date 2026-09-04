package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Notice;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CreateNoticeCommand;
import com.nxtime.nxtime.dto.NoticeResponse;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.NoticeRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.NoticeService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NoticeServiceImpl implements NoticeService {

    private static final Logger log = LoggerFactory.getLogger(NoticeServiceImpl.class);

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    public NoticeServiceImpl(NoticeRepository noticeRepository,
                             UserRepository userRepository,
                             CompanyRepository companyRepository) {
        this.noticeRepository = noticeRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * REQUIRES_NEW y no REQUIRED, aunque hoy den lo mismo.
     *
     * Este método lo invoca {@link
     * com.nxtime.nxtime.notification.NotificationListener} desde un
     * método {@code @Async} + {@code AFTER_COMMIT}: hilo distinto, sin
     * transacción activa. Ahí REQUIRED abriría una transacción nueva
     * igualmente, así que la anotación parece redundante.
     *
     * El {@code @Async} es justamente la única razón por la que
     * REQUIRED bastaría, y eso es una trampa: si alguien lo quitara
     * -- para depurar un correo, por ejemplo -- el listener pasaría a
     * correr en el hilo del commit, con una transacción YA CONFIRMADA
     * todavía pegada al hilo, y con REQUIRED este INSERT se uniría a
     * esa transacción muerta y se descartaría EN SILENCIO. Con
     * REQUIRES_NEW eso no puede pasar.
     *
     * {@code getReferenceById} en vez de {@code findById}: solo hacen
     * falta las claves ajenas, y un proxy atado a esta sesión es lo que
     * Hibernate necesita para escribirlas sin cargar dos filas más.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publicar(CreateNoticeCommand comando) {
        Notice aviso = Notice.builder()
                .empresa(companyRepository.getReferenceById(comando.empresaId()))
                .destinatario(userRepository.getReferenceById(comando.destinatarioId()))
                .tipo(comando.tipo())
                .titulo(comando.titulo())
                .cuerpo(comando.cuerpo())
                .rutaDestino(comando.rutaDestino())
                .creadoEn(Instant.now())
                .build();

        noticeRepository.save(aviso);
        log.debug("Aviso {} publicado para el usuario {}", comando.tipo(), comando.destinatarioId());
    }

    @Override
    public List<NoticeResponse> getMisAvisos(User destinatario) {
        return noticeRepository.findTop50ByDestinatarioOrderByCreadoEnDesc(destinatario).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public long contarNoLeidos(User destinatario) {
        return noticeRepository.countByDestinatarioAndLeidoFalse(destinatario);
    }

    @Override
    @Transactional
    public void marcarLeido(long avisoId, User destinatario) {
        Notice aviso = noticeRepository.findById(avisoId)
                .orElseThrow(() -> new ResourceNotFoundException("Aviso no encontrado."));

        // OJO: esto NO es la comprobación multi-tenant habitual del
        // proyecto (ver AbsenceServiceImpl o TimeEntryServiceImpl, que
        // comparan empresas). Aquí se compara la PERSONA: dos
        // compañeros de la misma empresa no deben poder tocarse los
        // avisos el uno al otro. Se parece al patrón de al lado, pero
        // es más estricto a propósito.
        if (aviso.getDestinatario().getId() != destinatario.getId()) {
            throw new TenantAccessException("No puedes marcar avisos de otra persona.");
        }

        // Idempotente: marcar dos veces no es un error, es un usuario
        // que ha tocado dos veces.
        if (!aviso.isLeido()) {
            aviso.setLeido(true);
            noticeRepository.save(aviso);
        }
    }

    @Override
    @Transactional
    public int marcarTodosLeidos(User destinatario) {
        List<Notice> pendientes = noticeRepository.findByDestinatarioAndLeidoFalse(destinatario);
        pendientes.forEach(aviso -> aviso.setLeido(true));
        noticeRepository.saveAll(pendientes);
        return pendientes.size();
    }

    private NoticeResponse toResponse(Notice aviso) {
        return new NoticeResponse(
                aviso.getId(),
                aviso.getTipo(),
                aviso.getTitulo(),
                aviso.getCuerpo(),
                aviso.getRutaDestino(),
                aviso.isLeido(),
                aviso.getCreadoEn());
    }
}
