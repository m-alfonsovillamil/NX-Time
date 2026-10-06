package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.PushDevice;
import com.nxtime.nxtime.domain.PushPlatform;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.PushDeviceRepository;
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

    public PushDeviceServiceImpl(PushDeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    public void registrar(User actor, String token, PushPlatform plataforma) {
        String limpio = token.strip();
        // Solo para el log: quién lo tenía. Lo que decide de quién es el token
        // es la sentencia de abajo, no esta lectura.
        deviceRepository.findByToken(limpio)
                .map(existente -> existente.getUsuario().getId())
                .filter(anterior -> anterior != actor.getId())
                .ifPresent(anterior -> log.info("Un dispositivo de push cambia de dueño (usuario {} -> {}).",
                        anterior, actor.getId()));
        deviceRepository.registrar(actor.getId(), plataforma.name(), limpio, Instant.now());

        List<PushDevice> suyos = deviceRepository.findByUsuario_IdOrderByRegistradoEnDesc(actor.getId());
        if (suyos.size() > DISPOSITIVOS_POR_PERSONA) {
            List<PushDevice> sobran = suyos.stream()
                    .sorted(Comparator.comparing(PushDevice::getVistoEn).reversed())
                    .skip(DISPOSITIVOS_POR_PERSONA)
                    .toList();
            // En una sola sentencia y sin contar filas: si dos registros a la
            // vez dan de baja el mismo dispositivo viejo, el segundo no borra
            // nada y no pasa nada. Con deleteAll, Hibernate esperaba borrar una
            // fila por entidad y el segundo registro fallaba entero.
            deviceRepository.deleteAllInBatch(sobran);
            log.info("Usuario {}: {} dispositivos de push viejos dados de baja (tope {}).",
                    actor.getId(), sobran.size(), DISPOSITIVOS_POR_PERSONA);
        }
    }

    @Override
    public void darDeBaja(User actor, String token) {
        deviceRepository.deleteByTokenAndUsuario(token.strip(), actor.getId());
    }
}
