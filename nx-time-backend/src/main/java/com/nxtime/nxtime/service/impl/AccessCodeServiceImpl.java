package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AccessCode;
import com.nxtime.nxtime.domain.AccessCodeType;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.notification.EmailNotSentException;
import com.nxtime.nxtime.notification.EmailSender;
import com.nxtime.nxtime.repository.AccessCodeRepository;
import com.nxtime.nxtime.repository.RefreshTokenRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AccessCodeService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver {@link AccessCodeService} y ADR 014.
 */
@Service
@Transactional(readOnly = true)
public class AccessCodeServiceImpl implements AccessCodeService {

    private static final Logger log = LoggerFactory.getLogger(AccessCodeServiceImpl.class);

    /**
     * Códigos que se pueden mandar a una misma cuenta en una hora. Frena el
     * bombardeo de correos a alguien, y de paso limita a 15 los intentos
     * por hora contra una cuenta (3 códigos por 5 intentos cada uno).
     */
    static final int MAXIMO_CODIGOS_POR_HORA = 3;

    /** El mismo para todo lo que no vale: ver {@link AccessCodeService#confirmar}. */
    static final String CODIGO_NO_VALIDO = "El código no es válido o ha caducado. Pide uno nuevo.";

    private final AccessCodeRepository accessCodeRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final Clock clock;
    private final SecureRandom azar = new SecureRandom();

    /**
     * El hash de un código cualquiera, para gastar lo mismo en BCrypt
     * cuando el correo no tiene cuenta. Sin esto, "no existe" respondería
     * en un milisegundo y "código incorrecto" en decenas, y el tiempo de
     * respuesta diría qué correos tienen cuenta aunque el mensaje no lo diga.
     */
    private final String hashDeRelleno;

    @Autowired
    public AccessCodeServiceImpl(
            AccessCodeRepository accessCodeRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender) {
        this(accessCodeRepository, userRepository, refreshTokenRepository, passwordEncoder, emailSender,
                Clock.systemUTC());
    }

    AccessCodeServiceImpl(
            AccessCodeRepository accessCodeRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            Clock clock) {
        this.accessCodeRepository = accessCodeRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.clock = clock;
        this.hashDeRelleno = passwordEncoder.encode("000000");
    }

    @Override
    @Transactional
    public void emitirCodigoDeAlta(User usuario, String nombreEmpresa) {
        String codigo = generarCodigo();
        guardar(usuario, AccessCodeType.ALTA, codigo);

        Map<String, Object> variables = variables(usuario, codigo, AccessCodeType.ALTA);
        variables.put("nombreEmpresa", nombreEmpresa);
        try {
            emailSender.enviarObligatorio(
                    usuario.getEmail(), "Tu cuenta de NX Time", "access-code-welcome", variables);
        } catch (EmailNotSentException e) {
            log.error("No se pudo enviar el código de alta a {}: {}", usuario.getEmail(), e.getCause().getMessage());
            // Al lanzar, la transacción del alta se deshace: ni cuenta ni código.
            throw new BusinessException(
                    "No se ha podido enviar el correo con el código de acceso, así que la cuenta no se ha creado. "
                            + "Inténtalo de nuevo más tarde.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    @Transactional
    public void solicitarRecuperacion(String email) {
        Optional<User> cuenta = userRepository.findByEmail(email.trim()).filter(User::isActivo);
        if (cuenta.isEmpty()) {
            // Sin el correo en el log: sería un registro de qué direcciones
            // se están probando.
            log.info("Recuperación pedida para un correo sin cuenta activa.");
            return;
        }
        User usuario = cuenta.get();

        Instant haceUnaHora = clock.instant().minus(Duration.ofHours(1));
        if (accessCodeRepository.countByUsuarioAndCreadoEnAfter(usuario, haceUnaHora) >= MAXIMO_CODIGOS_POR_HORA) {
            log.warn("Recuperación denegada para {}: ya tiene {} códigos en la última hora.",
                    usuario.getEmail(), MAXIMO_CODIGOS_POR_HORA);
            return;
        }

        /*
         * Primero el correo y después el código, al revés que en el alta. Si
         * el correo falla no se guarda nada, así que el código anterior, si lo
         * había, sigue valiendo. Y no se lanza: un error solo para los correos
         * que SÍ tienen cuenta diría cuáles la tienen. Queda en el log, que
         * con Sentry es un aviso a quien mantiene el servicio.
         */
        String codigo = generarCodigo();
        try {
            emailSender.enviarObligatorio(usuario.getEmail(), "Tu código para entrar en NX Time",
                    "access-code-recovery", variables(usuario, codigo, AccessCodeType.RECUPERACION));
        } catch (EmailNotSentException e) {
            log.error("No se pudo enviar el código de recuperación a {}: {}",
                    usuario.getEmail(), e.getCause().getMessage());
            return;
        }
        guardar(usuario, AccessCodeType.RECUPERACION, codigo);
        log.info("Código de recuperación enviado a {}", usuario.getEmail());
    }

    /*
     * noRollbackFor, y es lo más importante de esta clase: un código
     * incorrecto suma un intento Y lanza. Con un @Transactional normal, la
     * excepción desharía también la suma, y los intentos serían infinitos.
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public void confirmar(String email, String codigo, String contrasenaNueva) {
        Instant ahora = clock.instant();
        String limpio = codigo.strip();

        Optional<AccessCode> vigente = userRepository.findByEmail(email.trim())
                .filter(User::isActivo)
                .flatMap(accessCodeRepository::findFirstByUsuarioAndUsadoEnIsNullAndAnuladoEnIsNullOrderByCreadoEnDesc)
                .filter(candidato -> candidato.estaVigente(ahora));

        if (vigente.isEmpty()) {
            passwordEncoder.matches(limpio, hashDeRelleno); // mismo coste: ver hashDeRelleno
            throw new BusinessException(CODIGO_NO_VALIDO, HttpStatus.BAD_REQUEST);
        }

        AccessCode codigoAcceso = vigente.get();
        if (!passwordEncoder.matches(limpio, codigoAcceso.getCodigoHash())) {
            codigoAcceso.setIntentosFallidos(codigoAcceso.getIntentosFallidos() + 1);
            if (codigoAcceso.getIntentosFallidos() >= AccessCode.MAXIMO_INTENTOS) {
                codigoAcceso.setAnuladoEn(ahora);
                log.warn("Código de acceso de {} anulado tras {} intentos fallidos.",
                        codigoAcceso.getUsuario().getEmail(), AccessCode.MAXIMO_INTENTOS);
            }
            accessCodeRepository.save(codigoAcceso);
            throw new BusinessException(CODIGO_NO_VALIDO, HttpStatus.BAD_REQUEST);
        }

        User usuario = codigoAcceso.getUsuario();
        codigoAcceso.setUsadoEn(ahora);
        accessCodeRepository.save(codigoAcceso);
        usuario.setContrasena(passwordEncoder.encode(contrasenaNueva));
        userRepository.save(usuario);

        // Quien tuviera la sesión abierta con la contraseña anterior -- que
        // puede ser justo quien la robó -- deja de tenerla.
        int cerradas = refreshTokenRepository.revocarTodasLasDe(usuario);
        log.info("Contraseña fijada con un código de {} para {} ({} sesiones cerradas).",
                codigoAcceso.getTipo(), usuario.getEmail(), cerradas);
    }

    private void guardar(User usuario, AccessCodeType tipo, String codigo) {
        Instant ahora = clock.instant();
        // Solo vale el último que se ha mandado.
        accessCodeRepository.findByUsuarioAndUsadoEnIsNullAndAnuladoEnIsNull(usuario)
                .forEach(anterior -> anterior.setAnuladoEn(ahora));
        accessCodeRepository.save(AccessCode.builder()
                .usuario(usuario)
                .tipo(tipo)
                .codigoHash(passwordEncoder.encode(codigo))
                .creadoEn(ahora)
                .expiraEn(ahora.plus(tipo.getValidez()))
                .build());
    }

    /** Seis dígitos, ceros a la izquierda incluidos. {@link SecureRandom}: que no se pueda predecir el siguiente. */
    private String generarCodigo() {
        return String.format("%06d", azar.nextInt(1_000_000));
    }

    private static Map<String, Object> variables(User usuario, String codigo, AccessCodeType tipo) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("nombre", usuario.getNombre());
        variables.put("email", usuario.getEmail());
        variables.put("codigo", codigo);
        variables.put("validez", describir(tipo.getValidez()));
        return variables;
    }

    /** "24 horas", "1 hora", "15 minutos": lo que se lee en el correo. */
    static String describir(Duration validez) {
        long horas = validez.toHours();
        if (horas >= 1) {
            return horas + (horas == 1 ? " hora" : " horas");
        }
        return validez.toMinutes() + " minutos";
    }
}
