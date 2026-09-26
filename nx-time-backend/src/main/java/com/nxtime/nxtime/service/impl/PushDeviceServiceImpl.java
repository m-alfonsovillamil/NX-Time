package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.PushDevice;
import com.nxtime.nxtime.domain.PushPlatform;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.PushDeviceRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.PushDeviceService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ver {@link PushDeviceService}. */
@Service
@Transactional
public class PushDeviceServiceImpl implements PushDeviceService {

    private static final Logger log = LoggerFactory.getLogger(PushDeviceServiceImpl.class);

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
    }

    @Override
    public void darDeBaja(User actor, String token) {
        deviceRepository.deleteByTokenAndUsuario(token.strip(), actor.getId());
    }
}
