package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.AccessCode;
import com.nxtime.nxtime.domain.AccessCodeType;
import com.nxtime.nxtime.domain.Emails;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.notification.EmailNotSentException;
import com.nxtime.nxtime.notification.EmailSender;
import com.nxtime.nxtime.repository.AccessCodeRepository;
import com.nxtime.nxtime.repository.RefreshTokenRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AccessCodeService;
import com.nxtime.nxtime.notification.NotificationEvents;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

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
    private final String urlDescargaApp;
    private final SecureRandom azar = new SecureRandom();

    /**
     * El hash de un código cualquiera, para gastar lo mismo en BCrypt
     * cuando el correo no tiene cuenta. Sin esto, "no existe" respondería
     * en un milisegundo y "código incorrecto" en decenas, y el tiempo de
     * respuesta diría qué correos tienen cuenta aunque el mensaje no lo diga.
     */
    private final String hashDeRelleno;

    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public AccessCodeServiceImpl(
            AccessCodeRepository accessCodeRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            ApplicationEventPublisher eventPublisher,
            @Value("${application.app.download-url:}") String urlDescargaApp) {
        this(accessCodeRepository, userRepository, refreshTokenRepository, passwordEncoder, emailSender,
                eventPublisher, Clock.systemUTC(), urlDescargaApp);
    }

    AccessCodeServiceImpl(
            AccessCodeRepository accessCodeRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            String urlDescargaApp) {
        this.accessCodeRepository = accessCodeRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.urlDescargaApp = urlDescargaApp == null ? "" : urlDescargaApp.trim();
        this.hashDeRelleno = passwordEncoder.encode("000000");
    }

    /**
     * El alta SÍ manda el correo dentro de la transacción, y se queda así.
     *
     * Es el único envío que lo hace, al revés que la recuperación (ver
     * {@link #solicitarRecuperacion}, Fase A9). La diferencia no es un
     * descuido:
     *
     * <ul>
     *   <li><b>La garantía vale más aquí.</b> Si el correo no sale, la cuenta
     *       no se crea (ADR 014). Lo contrario deja a alguien dado de alta sin
     *       forma de entrar y sin que nadie se entere, que es peor que un alta
     *       que falla a la cara de quien la hace.</li>
     *   <li><b>El riesgo es mucho menor.</b> Esto no es público: exige la
     *       authority {@code empleado:crear} y lo hace una persona de una en
     *       una. La recuperación era un endpoint abierto con diez peticiones
     *       por minuto y por IP, y ahí la transacción larga era un vector para
     *       dejar sin conexiones al resto de la aplicación.</li>
     * </ul>
     *
     * Se valoró sacarlo con una transacción compensatoria --confirmar, enviar
     * fuera y borrar la cuenta si falla-- y se descartó: cambia una garantía
     * atómica por una ventana en la que, si el proceso muere entre el commit y
     * la compensación, queda una cuenta a la que nunca se le mandó el código.
     * Lo que sí se ha hecho es acotar la espera bajando los timeouts SMTP de
     * 5 s a 3 s (ver application.yml).
     */
    @Override
    @Transactional
    public void emitirCodigoDeAlta(User usuario, String nombreEmpresa) {
        String codigo = generarCodigo();
        guardar(usuario, AccessCodeType.ALTA, codigo);

        Map<String, Object> variables = variables(usuario, codigo, AccessCodeType.ALTA);
        variables.put("nombreEmpresa", nombreEmpresa);
        // Solo en el alta: quien recupera la contraseña ya tiene la app.
        variables.put("urlDescargaApp", urlDescargaApp);
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
        Optional<User> cuenta = userRepository.findByEmail(Emails.normalizar(email)).filter(User::isActivo);
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
         * El código se guarda y el correo sale DESPUÉS de confirmar, a través
         * de un evento AFTER_COMMIT + @Async (Fase A9).
         *
         * Antes se mandaba aquí mismo, dentro de la transacción, y primero:
         * si el correo fallaba no se guardaba nada, así que el código anterior
         * seguía valiendo. Esa propiedad era bonita y salía cara. Este
         * endpoint es PÚBLICO y admite diez peticiones por minuto y por IP;
         * con el SMTP dentro, cada una retenía una conexión del pool --que en
         * producción es de cinco-- durante todo lo que tardara el servidor de
         * correo en contestar o en agotar sus esperas. Bastaba con que el SMTP
         * se atascara para dejar sin conexiones al resto de la aplicación.
         *
         * Lo que se pierde: si el correo no sale, el código nuevo ya ha
         * anulado al anterior y esta persona tiene que volver a pedirlo. Es
         * una molestia, y la alternativa era un vector para tumbar el
         * servicio entero desde fuera.
         *
         * Lo que NO cambia: la respuesta sigue siendo la misma exista o no la
         * cuenta, y un fallo de envío sigue quedándose en el log en vez de
         * propagarse --un error solo para los correos que SÍ tienen cuenta
         * diría cuáles la tienen--.
         */
        String codigo = generarCodigo();
        guardar(usuario, AccessCodeType.RECUPERACION, codigo);
        eventPublisher.publishEvent(new NotificationEvents.AccessCodeRequested(
                usuario.getEmail(), variables(usuario, codigo, AccessCodeType.RECUPERACION)));
        log.info("Código de recuperación emitido para {}", usuario.getEmail());
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

        Optional<AccessCode> vigente = userRepository.findByEmail(Emails.normalizar(email))
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
        int cerradas = refreshTokenRepository.revocarTodasLasDe(usuario, ahora);
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
