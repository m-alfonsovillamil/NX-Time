package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.KioskDtos.KioskCard;
import com.nxtime.nxtime.dto.KioskDtos.MyKioskStatus;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.kiosco.TarjetaDeKiosco;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.KioskProfileService;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver {@link KioskProfileService}.
 *
 * La persona se vuelve a leer dentro de la transacción: la que trae la sesión
 * viene del filtro de seguridad, fuera de ella, y guardar sobre esa copia se
 * saltaría el {@code @Version}.
 */
@Service
@Transactional(readOnly = true)
public class KioskProfileServiceImpl implements KioskProfileService {

    private static final Logger log = LoggerFactory.getLogger(KioskProfileServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TarjetaDeKiosco tarjeta;

    public KioskProfileServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
            TarjetaDeKiosco tarjeta) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tarjeta = tarjeta;
    }

    @Override
    public MyKioskStatus estado(User actor) {
        return estadoDe(releer(actor));
    }

    @Override
    @Transactional
    public MyKioskStatus fijarPin(User actor, String pin) {
        if (demasiadoFacil(pin)) {
            throw new BusinessException("Ese PIN es demasiado fácil de adivinar: nada de cifras repetidas "
                    + "ni seguidas, como 1111 o 1234.", HttpStatus.BAD_REQUEST);
        }
        User persona = releer(actor);
        persona.setKioscoPinHash(passwordEncoder.encode(pin));
        // Un PIN nuevo empieza sin fallos: quien se bloqueó y lo cambia ya sabe cuál es.
        persona.setKioscoPinFallos(0);
        persona.setKioscoPinBloqueadoHasta(null);
        // Un PIN nuevo empieza sin bloqueos: los de antes eran contra el otro (V38).
        persona.setKioscoPinBloqueos(0);
        return estadoDe(userRepository.save(persona));
    }

    @Override
    @Transactional
    public MyKioskStatus quitarPin(User actor) {
        User persona = releer(actor);
        persona.setKioscoPinHash(null);
        persona.setKioscoPinFallos(0);
        persona.setKioscoPinBloqueadoHasta(null);
        persona.setKioscoPinBloqueos(0);
        return estadoDe(userRepository.save(persona));
    }

    @Override
    @Transactional
    public KioskCard tarjeta(User actor) {
        User persona = releer(actor);
        if (persona.getKioscoTarjetaVersion() == null) {
            persona.setKioscoTarjetaVersion(1);
            persona = userRepository.save(persona);
        }
        return tarjetaDe(persona);
    }

    @Override
    @Transactional
    public KioskCard regenerarTarjeta(User actor) {
        User persona = releer(actor);
        Integer actual = persona.getKioscoTarjetaVersion();
        persona.setKioscoTarjetaVersion(actual == null ? 1 : actual + 1);
        return tarjetaDe(userRepository.save(persona));
    }

    @Override
    @Transactional
    public List<KioskCard> tarjetasDeLaEmpresa(User actor) {
        List<User> plantilla = userRepository.findByEmpresaAndActivoTrue(actor.getEmpresa());
        // Con estas tarjetas se ficha por cualquiera: que quede quién las sacó.
        log.warn("El usuario {} ha sacado las tarjetas de kiosco de {} personas de la empresa {}.",
                actor.getId(), plantilla.size(), actor.getEmpresa().getId());
        return plantilla.stream()
                .map(persona -> {
                    if (persona.getKioscoTarjetaVersion() == null) {
                        persona.setKioscoTarjetaVersion(1);
                        return userRepository.save(persona);
                    }
                    return persona;
                })
                .sorted(Comparator.comparing(User::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(this::tarjetaDe)
                .toList();
    }

    /**
     * Sin cifras repetidas (1111) ni seguidas hacia arriba o hacia abajo (1234,
     * 9876): son los primeros que prueba cualquiera delante de una tablet.
     */
    static boolean demasiadoFacil(String pin) {
        boolean iguales = pin.chars().distinct().count() == 1;
        boolean subiendo = true;
        boolean bajando = true;
        for (int i = 1; i < pin.length(); i++) {
            int diferencia = pin.charAt(i) - pin.charAt(i - 1);
            subiendo &= diferencia == 1;
            bajando &= diferencia == -1;
        }
        return iguales || subiendo || bajando;
    }

    private KioskCard tarjetaDe(User persona) {
        String codigo = tarjeta.codigo(persona.getId(), persona.getKioscoTarjetaVersion());
        return new KioskCard(persona.getId(), persona.getNombre(), codigo, tarjeta.svg(codigo));
    }

    private static MyKioskStatus estadoDe(User persona) {
        return new MyKioskStatus(persona.getKioscoPinHash() != null, persona.getKioscoTarjetaVersion() != null,
                persona.getKioscoPinBloqueadoHasta());
    }

    private User releer(User actor) {
        return userRepository.findById(actor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));
    }
}
