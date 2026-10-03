package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.PushDevice;
import com.nxtime.nxtime.domain.PushPlatform;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.PushDeviceRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.PushDeviceService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ver {@link PushDeviceService}. */
@Service
@Transactional
public class PushDeviceServiceImpl implements PushDeviceService {

    private static final Logger log = LoggerFactory.getLogger(PushDeviceServiceImpl.class);

    /**
     * Los dispositivos que se guardan por persona (revisión de seguridad del
     * 1/10/2026). Cada navegador en que se encienden las notificaciones es uno,
     * y sin tope una cuenta podía registrar tokens sin fin: cada aviso suyo
     * saldría hacia todos. Se quedan los más recientes; el más viejo, que es
     * casi seguro un navegador que ya no se usa, deja de recibir.
     */
    static final int DISPOSITIVOS_POR_PERSONA = 10;

    private final PushDeviceRepository deviceRepository;
    private final UserRepository userRepository;

    public PushDeviceServiceImpl(PushDeviceRepository deviceRepository, UserRepository userRepository) {
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void registrar(User actor, String token, PushPlatform plataforma) {
        String limpio = token.strip();
        User dueno = userRepository.getReferenceById(actor.getId());
        deviceRepository.findByToken(limpio).ifPresentOrElse(
                existente -> {
                    if (existente.getUsuario().getId() != actor.getId()) {
                        log.info("Un dispositivo de push cambia de dueño (usuario {} -> {}).",
                                existente.getUsuario().getId(), actor.getId());
                    }
                    existente.setUsuario(dueno);
                    existente.setPlataforma(plataforma);
                    existente.setVistoEn(Instant.now());
                },
                () -> deviceRepository.save(PushDevice.builder()
                        .usuario(dueno)
                        .plataforma(plataforma)
                        .token(limpio)
                        .build()));
        deviceRepository.flush();
        List<PushDevice> suyos = deviceRepository.findByUsuario_IdOrderByRegistradoEnDesc(actor.getId());
        if (suyos.size() > DISPOSITIVOS_POR_PERSONA) {
            List<PushDevice> sobran = suyos.stream()
                    .sorted(Comparator.comparing(PushDevice::getVistoEn).reversed())
                    .skip(DISPOSITIVOS_POR_PERSONA)
                    .toList();
            deviceRepository.deleteAll(sobran);
            log.info("Usuario {}: {} dispositivos de push viejos dados de baja (tope {}).",
                    actor.getId(), sobran.size(), DISPOSITIVOS_POR_PERSONA);
        }
    }

    @Override
    public void darDeBaja(User actor, String token) {
        deviceRepository.deleteByTokenAndUsuario(token.strip(), actor.getId());
    }
}
