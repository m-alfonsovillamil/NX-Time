package com.nxtime.nxtime.kiosco;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nxtime.nxtime.domain.Kiosk;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.KioskDtos.KioskCredential;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.UserRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quién es la persona que está delante del kiosco (ADR 033): por su tarjeta QR
 * o por su nombre y su PIN.
 *
 * <b>Los límites son por persona y por kiosco, no por IP</b>: toda la tablet
 * comparte una IP, y limitar por ella bloquearía a todo el centro a la vez.
 * <ul>
 *   <li>Cinco PIN fallidos seguidos bloquean el PIN de ESA persona quince
 *       minutos, acierte o no después. Los fallos se guardan en la base, y por
 *       eso esta clase no deshace su transacción al rechazar: el contador tiene
 *       que quedar escrito aunque la respuesta sea un error.</li>
 *   <li>Cada kiosco tiene un tope de identificaciones por minuto: más de las
 *       que caben en una cola de gente fichando, menos que las de alguien
 *       probando PIN de todos los nombres de la lista.</li>
 * </ul>
 *
 * Los mensajes no distinguen más de lo necesario: una tarjeta que no vale no
 * dice si es de otra empresa, de alguien de baja o de una versión vieja.
 */
@Component
public class IdentificacionEnKiosco {

    private static final Logger log = LoggerFactory.getLogger(IdentificacionEnKiosco.class);

    static final int FALLOS_HASTA_BLOQUEAR = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(15);
    static final int IDENTIFICACIONES_POR_MINUTO = 40;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TarjetaDeKiosco tarjeta;
    private final Clock clock;
    private final Cache<Long, Bucket> porKiosco = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(5))
            .build();

    @Autowired
    public IdentificacionEnKiosco(UserRepository userRepository, PasswordEncoder passwordEncoder,
            TarjetaDeKiosco tarjeta) {
        this(userRepository, passwordEncoder, tarjeta, Clock.systemUTC());
    }

    IdentificacionEnKiosco(UserRepository userRepository, PasswordEncoder passwordEncoder,
            TarjetaDeKiosco tarjeta, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tarjeta = tarjeta;
        this.clock = clock;
    }

    /**
     * La persona, si la credencial vale en este kiosco.
     *
     * @throws BusinessException 400 sin credencial, 404 si la tarjeta o la
     *     persona no valen aquí, 403 con el PIN equivocado, 429 con el PIN
     *     bloqueado o el kiosco por encima de su tope
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public User identificar(Kiosk kiosco, KioskCredential credencial) {
        Bucket cubo = porKiosco.get(kiosco.getId(), id -> Bucket.builder()
                .addLimit(Bandwidth.simple(IDENTIFICACIONES_POR_MINUTO, Duration.ofMinutes(1)))
                .build());
        if (!cubo.tryConsume(1)) {
            throw new BusinessException("Demasiados intentos en este kiosco. Espera un minuto.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        if (credencial.qr() != null && !credencial.qr().isBlank()) {
            return porTarjeta(kiosco, credencial.qr());
        }
        if (credencial.usuarioId() != null && credencial.pin() != null) {
            return porPin(kiosco, credencial.usuarioId(), credencial.pin());
        }
        throw new BusinessException("Pasa tu tarjeta o elige tu nombre y teclea tu PIN.", HttpStatus.BAD_REQUEST);
    }

    private User porTarjeta(Kiosk kiosco, String qr) {
        TarjetaDeKiosco.Lectura lectura = tarjeta.leer(qr).orElseThrow(KioscoNoVale::tarjeta);
        User persona = userRepository.findById(lectura.usuarioId())
                .filter(p -> esDeAqui(p, kiosco))
                // Una tarjeta regenerada deja sin valor a todas las de antes.
                .filter(p -> p.getKioscoTarjetaVersion() != null
                        && p.getKioscoTarjetaVersion() == lectura.version())
                .orElseThrow(KioscoNoVale::tarjeta);
        return persona;
    }

    private User porPin(Kiosk kiosco, long usuarioId, String pin) {
        User persona = userRepository.findById(usuarioId)
                .filter(p -> esDeAqui(p, kiosco) && p.getKioscoPinHash() != null)
                .orElseThrow(KioscoNoVale::persona);

        Instant ahora = clock.instant();
        if (persona.getKioscoPinBloqueadoHasta() != null && ahora.isBefore(persona.getKioscoPinBloqueadoHasta())) {
            throw new BusinessException("Tu PIN está bloqueado por demasiados intentos. Prueba dentro de un rato "
                    + "o ficha desde la app.", HttpStatus.TOO_MANY_REQUESTS);
        }

        if (!passwordEncoder.matches(pin, persona.getKioscoPinHash())) {
            int fallos = persona.getKioscoPinFallos() + 1;
            if (fallos >= FALLOS_HASTA_BLOQUEAR) {
                persona.setKioscoPinFallos(0);
                persona.setKioscoPinBloqueadoHasta(ahora.plus(BLOQUEO));
                log.warn("PIN de kiosco de {} bloqueado tras {} fallos (kiosco {}).",
                        persona.getId(), FALLOS_HASTA_BLOQUEAR, kiosco.getId());
            } else {
                persona.setKioscoPinFallos(fallos);
            }
            userRepository.save(persona);
            throw new BusinessException("PIN incorrecto.", HttpStatus.FORBIDDEN);
        }

        if (persona.getKioscoPinFallos() != 0 || persona.getKioscoPinBloqueadoHasta() != null) {
            persona.setKioscoPinFallos(0);
            persona.setKioscoPinBloqueadoHasta(null);
            userRepository.save(persona);
        }
        return persona;
    }

    /** De la empresa del kiosco y de alta. Una tarjeta de otra empresa no vale aquí. */
    private static boolean esDeAqui(User persona, Kiosk kiosco) {
        return persona.isActivo()
                && persona.getEmpresa() != null
                && persona.getEmpresa().getId() == kiosco.getEmpresa().getId();
    }

    /** Los rechazos que no dicen más de la cuenta. */
    private static final class KioscoNoVale {

        static BusinessException tarjeta() {
            return new BusinessException("Esta tarjeta no vale en este kiosco. Si la has cambiado, usa la nueva.",
                    HttpStatus.NOT_FOUND);
        }

        static BusinessException persona() {
            return new BusinessException("Esa persona no puede fichar en este kiosco.", HttpStatus.NOT_FOUND);
        }
    }
}
