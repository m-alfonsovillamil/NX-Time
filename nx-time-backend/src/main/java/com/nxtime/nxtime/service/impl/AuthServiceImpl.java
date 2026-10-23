package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.config.CondicionesDelServicio;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.CompanyStatus;
import com.nxtime.nxtime.domain.RefreshToken;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.ChangePasswordRequest;
import com.nxtime.nxtime.dto.ConfirmRegistrationRequest;
import com.nxtime.nxtime.dto.CreateEmployeeRequest;
import com.nxtime.nxtime.dto.CreateManagerRequest;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import com.nxtime.nxtime.dto.RegistrationPendingResponse;
import com.nxtime.nxtime.dto.SegundoPasoRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.RefreshTokenRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.JwtService;
import com.nxtime.nxtime.security.LimitadorDeIntentosPorCuenta;
import com.nxtime.nxtime.security.OperadoresDePlataforma;
import com.nxtime.nxtime.security.SegundoFactorObligatorio;
import com.nxtime.nxtime.security.TurnoDeContrasena;
import com.nxtime.nxtime.service.AccessCodeService;
import com.nxtime.nxtime.service.AuthService;
import com.nxtime.nxtime.service.CompanyLifecycleService;
import com.nxtime.nxtime.service.SegundoFactorService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implementación de la lógica de negocio para Usuarios y Seguridad.
 *
 * Desde la Fase 4: registerManager crea un ADMIN (no un GESTOR) --
 * quien funda el tenant administra la empresa, y es quien puede crear
 * después GESTOR/RRHH/otros ADMIN (ver RoleAuthorities, "gestor:crear").
 * Antes cualquier GESTOR podía crear otro GESTOR sin límite (ver
 * auditoría, defectos de diseño).
 *
 * Desde el 09/2026 (ADR 014), quien da un alta ya no pone la contraseña
 * de nadie: la cuenta nace sin contraseña utilizable y su dueño elige la
 * suya con el código que le llega por correo.
 */
@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /**
     * Lo que responde el login (403) a quien registró una empresa y no ha
     * confirmado su correo. Los clientes lo reconocen por el 403, que el login
     * no da por nada más, y pasan a la pantalla del código.
     */
    static final String CORREO_SIN_CONFIRMAR =
            "Falta confirmar tu correo. Te hemos mandado un código: escríbelo para entrar.";

    /**
     * Lo que tiene que pasar entre dos correos de «ya tienes cuenta» a la
     * misma cuenta (ADR 037). Un día: el correo no caduca ni lleva nada que
     * usar, así que repetirlo antes no ayuda a su dueño y sí a quien quiera
     * molestarle.
     */
    static final Duration ESPERA_ENTRE_AVISOS = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher;
    private final AccessCodeService accessCodeService;
    private final LimitadorDeIntentosPorCuenta limitadorPorCuenta;
    private final OperadoresDePlataforma operadores;

    /** Para lo que escribe una entrada, que es lo único de ella que va en transacción: ver {@link #login}. */
    private final TransactionTemplate escritura;

    private final TurnoDeContrasena turnoDeContrasena;
    private final CondicionesDelServicio servicio;
    private final CompanyLifecycleService cicloDeLaEmpresa;
    private final SegundoFactorService segundoFactor;
    private final SegundoFactorObligatorio segundoFactorObligatorio;

    @Value("${application.security.jwt.refresh-expiration}")
    private long refreshExpirationMillis;

    /**
     * Cuánto dura la sesión de un navegador sin usarse (ADR 041). Sale de la
     * configuración y no de {@link RefreshToken.Origen}, que deja solo su
     * valor por defecto: una instalación con ordenadores compartidos puede
     * querer bajarla sin tocar código.
     */
    @Value("${application.security.web-session.duracion}")
    private Duration vidaDeLaSesionWeb = RefreshToken.Origen.WEB.duracion();

    public AuthServiceImpl(
            UserRepository userRepository,
            CompanyRepository companyRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            ApplicationEventPublisher eventPublisher,
            AccessCodeService accessCodeService,
            LimitadorDeIntentosPorCuenta limitadorPorCuenta,
            OperadoresDePlataforma operadores,
            PlatformTransactionManager transacciones,
            TurnoDeContrasena turnoDeContrasena,
            CondicionesDelServicio servicio,
            CompanyLifecycleService cicloDeLaEmpresa,
            SegundoFactorService segundoFactor,
            SegundoFactorObligatorio segundoFactorObligatorio
    ) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.eventPublisher = eventPublisher;
        this.accessCodeService = accessCodeService;
        this.limitadorPorCuenta = limitadorPorCuenta;
        this.operadores = operadores;
        this.escritura = new TransactionTemplate(transacciones);
        this.turnoDeContrasena = turnoDeContrasena;
        this.servicio = servicio;
        this.cicloDeLaEmpresa = cicloDeLaEmpresa;
        this.segundoFactor = segundoFactor;
        this.segundoFactorObligatorio = segundoFactorObligatorio;
    }

    // Desde la Fase 3 (PostgreSQL + IDENTITY) esto es una transacción
    // normal: los dos save() (Company y User) son atómicos de verdad.
    @Override
    @Transactional
    public RegistrationPendingResponse registerManager(RegisterManagerRequest request) {
        // El hash de la contraseña, se use o no: así el registro cuesta lo
        // mismo con un correo que ya tiene cuenta que con uno nuevo, y el
        // tiempo de respuesta no dice cuál es cuál.
        String contrasena = passwordEncoder.encode(request.contrasena());
        Optional<User> existente = userRepository.findByEmail(request.email());

        if (existente.isPresent() && !existente.get().correoPendienteDeConfirmar()) {
            // Ese correo ya es de alguien. Aquí no se dice (enumeraría
            // cuentas): la respuesta es la de siempre. Se le dice a su dueño,
            // por correo (ADR 037).
            passwordEncoder.encode(contrasena); // el BCrypt del código que no se emite
            avisarDeQueYaTieneCuenta(existente.get());
            return pendienteDeConfirmar(request.email());
        }

        User usuario;
        if (existente.isPresent()) {
            // Un registro que se quedó a medias con este correo (el código no
            // llegó, caducó, o alguien probó con un correo que no es suyo): se
            // rehace con los datos nuevos y sale otro código. Quien no tenga
            // el buzón sigue sin poder entrar.
            usuario = existente.get();
            Company suya = usuario.getEmpresa();
            if (!suya.getNombre().equals(request.nombreEmpresa())) {
                exigirNombreLibre(request.nombreEmpresa());
                suya.setNombre(request.nombreEmpresa());
                companyRepository.save(suya);
            }
            usuario.setNombre(request.nombre());
            usuario.setApellidos(request.apellidos());
            // Con la MISMA contraseña es su dueño repitiendo porque el código no
            // llegó. Con otra, no hay forma de saber cuál de los dos es el dueño
            // del correo, y hasta el 17/10/2026 ganaba la última: quien
            // registraba encima de un alta a medias ajena se quedaba la cuenta
            // en cuanto el dueño canjeaba el código, que le llega a él (plan J).
            // Guardar cada contraseña con su código no lo arregla: «solo vale
            // el último código», y el último sería el del otro. Así que, si
            // hay dos, no gana ninguna: el dueño entra al canjear el código,
            // como siempre, y elige contraseña con «He olvidado mi contraseña».
            boolean laMisma = passwordEncoder.matches(request.contrasena(), usuario.getContrasena());
            if (!laMisma) {
                log.warn("Registro a medias del usuario {} repetido con otra contraseña: se queda sin ninguna.",
                        usuario.getId());
            }
            usuario.setContrasena(laMisma ? contrasena : contrasenaInutilizable());
            usuario.setCorreoSinConfirmarDesde(Instant.now());
            usuario = userRepository.save(usuario);
            suya.aceptarCondiciones(servicio.version(), usuario, Instant.now());
            companyRepository.save(suya);
        } else {
            exigirNombreLibre(request.nombreEmpresa());
            // Pendiente de aprobar desde el principio (ADR 046), aunque hasta
            // confirmar el correo no se avisa a nadie: no se sabe si es suyo.
            Company company = companyRepository.save(Company.builder().nombre(request.nombreEmpresa())
                    .estado(servicio.requiereAprobacion() ? CompanyStatus.PENDIENTE : CompanyStatus.ACTIVA)
                    .build());
            usuario = userRepository.save(User.builder()
                    .nombre(request.nombre())
                    .apellidos(request.apellidos())
                    .email(request.email())
                    .contrasena(contrasena)
                    .rol(Role.ADMIN)
                    .empresa(company)
                    .correoSinConfirmarDesde(Instant.now())
                    .build());
            // La casilla del formulario, que el DTO exige marcada.
            company.aceptarCondiciones(servicio.version(), usuario, Instant.now());
            companyRepository.save(company);
        }

        accessCodeService.emitirCodigoDeConfirmacion(usuario);
        log.info("Empresa '{}' registrada; su ADMIN (usuario {}) tiene que confirmar el correo.",
                usuario.getEmpresa().getNombre(), usuario.getId());
        return pendienteDeConfirmar(request.email());
    }

    private void exigirNombreLibre(String nombreEmpresa) {
        if (companyRepository.findByNombre(nombreEmpresa).isPresent()) {
            throw new BusinessException("La empresa ya existe. Solicita acceso al administrador.");
        }
    }

    /**
     * El correo de «ya tienes cuenta» (ADR 037), a quien se registra con un
     * correo que ya la tiene: casi siempre es su dueño, que no se acordaba, y
     * sin este correo se queda esperando un código que no va a llegar.
     *
     * Sale después del commit y sin esperar, como el código de confirmación
     * (mismo evento), para que este camino y el de un correo nuevo sigan
     * pareciéndose también en lo que tardan. Y como mucho uno cada
     * {@link #ESPERA_ENTRE_AVISOS} por cuenta: el registro es público, y sin
     * tope serviría para llenarle el buzón a cualquiera que tenga cuenta.
     */
    private void avisarDeQueYaTieneCuenta(User usuario) {
        Instant ahora = Instant.now();
        if (userRepository.marcarAvisoDeCuentaExistente(
                usuario.getId(), ahora, ahora.minus(ESPERA_ENTRE_AVISOS)) == 0) {
            log.warn("Registro con un correo que ya tiene cuenta (usuario {}): ya se le avisó hace menos de {} h.",
                    usuario.getId(), ESPERA_ENTRE_AVISOS.toHours());
            return;
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("nombre", usuario.getNombre());
        variables.put("email", usuario.getEmail());
        variables.put("nombreEmpresa", usuario.getEmpresa().getNombre());
        // A quien está de baja no se le puede decir «entra»: no puede.
        variables.put("activa", usuario.isActivo());
        eventPublisher.publishEvent(new NotificationEvents.AccessCodeRequested(
                usuario.getEmail(), variables, "account-already-exists", "Ya tienes una cuenta en NX Time"));
        log.info("Registro con un correo que ya tiene cuenta (usuario {}): no se crea nada y se le avisa.",
                usuario.getId());
    }

    // No dice «un código»: a quien ya tenía cuenta le llega otro correo (ADR
    // 037), y este texto tiene que valer para los dos.
    private static RegistrationPendingResponse pendienteDeConfirmar(String email) {
        return new RegistrationPendingResponse(email,
                "Te hemos mandado un correo a " + email + " con el siguiente paso. Si no llega en unos minutos, "
                        + "mira en la carpeta de spam.");
    }

    /*
     * noRollbackFor, como AccessCodeServiceImpl#confirmar: un código
     * incorrecto suma un intento y lanza, y la suma tiene que quedarse.
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthenticationResponse confirmarRegistro(ConfirmRegistrationRequest request) {
        User usuario = accessCodeService.confirmarCorreo(request.email(), request.codigo());
        if (usuario.getEmpresa().getEstado() == CompanyStatus.PENDIENTE) {
            // Ahora sí se sabe que el correo es suyo: se avisa al operador, y
            // a esta persona se le dice que espere (423). Lanzar no deshace el
            // canje del código: ver el noRollbackFor de arriba.
            cicloDeLaEmpresa.avisarDeQueEspera(usuario.getEmpresa(), usuario);
            throw cicloDeLaEmpresa.noDisponible(usuario.getEmpresa());
        }
        return buildAuthResponse(usuario, origenDe(request.origen()), false);
    }

    // SIN transacción alrededor (ADR 041), y es lo que importa de este método:
    // comprobar la contraseña es BCrypt, unos 80 ms de CPU a propósito, y
    // hasta octubre de 2026 se hacía con una conexión a la base cogida. Con
    // cinco conexiones y poca CPU, unas cuantas entradas a la vez las
    // retenían todas mientras esperaban su turno de procesador, y nadie más
    // podía ni fichar: en la prueba de carga el pool llegó a tener cinco
    // ocupadas y noventa peticiones esperando (docs/PRUEBA-DE-CARGA.md).
    //
    // Y las contraseñas se comprueban por turnos (TurnoDeContrasena): la
    // transacción hacía también, sin querer, de tope de cuántas a la vez.
    //
    // Ahora la persona se lee en una transacción corta (la que le pone
    // ApplicationConfig#authenticationProvider, y hace falta que sea una: ver
    // allí), la contraseña se comprueba sin ninguna, y solo lo que escribe --la
    // sesión, o el código de confirmación-- abre la suya. Esa tiene que
    // confirmarse también cuando se responde 403 por no haber confirmado el
    // correo: el código que acaba de salir tiene que quedar guardado.
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthenticationResponse login(LoginRequest request) {
        // Además del límite por IP (LoginRateLimitFilter), un límite por
        // CUENTA: la IP sale de una cabecera y depende de los proxies que
        // haya delante, el correo al que se intenta entrar no. Es lo que
        // frena probar contraseñas de alguien desde muchos sitios a la vez.
        limitadorPorCuenta.comprobar(request.email());

        try {
            // Por turnos, tantas a la vez como procesadores: ver TurnoDeContrasena.
            turnoDeContrasena.conTurno(() -> authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.contrasena())));
        } catch (DisabledException e) {
            // La contraseña era buena: el estado se mira después de ella (ver
            // ApplicationConfig). O la cuenta está de baja, que sigue siendo un
            // intento fallido como siempre, o es su empresa la que no está
            // activa (ADR 046), y eso se le dice más abajo, con el correo
            // pendiente de confirmar por delante si lo tiene.
            Boolean deAlta = escritura.execute(estado -> userRepository.findByEmail(request.email())
                    .map(User::isActivo).orElse(false));
            if (!Boolean.TRUE.equals(deAlta)) {
                limitadorPorCuenta.fallo(request.email());
                throw e;
            }
        } catch (InternalAuthenticationServiceException e) {
            // No es la contraseña: es que no se ha podido leer a la persona.
            // Spring lo envuelve en una excepción de autenticación, y dejarla
            // pasar la convertía en «credenciales incorrectas» y en un intento
            // fallido de esa cuenta. Si es la base, que salga como lo que es
            // (503) y que no cuente contra nadie.
            if (e.getCause() instanceof DataAccessException || e.getCause() instanceof TransactionException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        } catch (org.springframework.security.core.AuthenticationException e) {
            // Cada fallo alarga la espera hasta el siguiente intento (ADR 034).
            limitadorPorCuenta.fallo(request.email());
            throw e;
        }
        limitadorPorCuenta.acierto(request.email());

        AuthenticationResponse sesion = escritura.execute(estado -> abrirSesionTrasLaContrasena(request));
        if (sesion == null) {
            // Fuera de la transacción, y no dentro: lanzarlo dentro la
            // desharía, y el código que acaba de salir tiene que quedar guardado.
            throw new BusinessException(CORREO_SIN_CONFIRMAR, HttpStatus.FORBIDDEN);
        }
        return sesion;
    }

    /**
     * Lo que escribe una entrada, ya con la contraseña comprobada.
     *
     * @return la sesión, o null si falta confirmar el correo (y entonces ha
     *         salido otro código)
     */
    private AuthenticationResponse abrirSesionTrasLaContrasena(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + request.email()));

        // Después de comprobar la contraseña, no antes: así el 403 solo lo ve
        // quien la sabe, y no dice a nadie más qué cuentas están a medias.
        if (user.correoPendienteDeConfirmar()) {
            accessCodeService.emitirCodigoDeConfirmacion(user);
            return null;
        }
        // Pendiente de aprobar o suspendida (ADR 046): 423 con lo que tiene que hacer.
        cicloDeLaEmpresa.exigirActiva(user.getEmpresa());

        // Con segundo factor, la contraseña no abre sesión: falta el código
        // (ADR 051). Lo que sale es un desafío de cinco minutos, que solo
        // sirve para mandarlo con el código a /auth/2fa.
        if (user.tieneSegundoFactor()) {
            log.info("Contraseña correcta, falta el segundo factor: {}", user.getId());
            return AuthenticationResponse.falta(jwtService.generarDesafio(user.getId(), huellaDe(user)));
        }

        log.info("Login correcto: {}", user.getId());
        return buildAuthResponse(user, origenDe(request.origen()), false);
    }

    /*
     * noRollbackFor: el código de recuperación que se gasta y el correo que lo
     * avisa tienen que quedarse aunque después algo falle, como en
     * confirmarRegistro. Y un código incorrecto lanza.
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthenticationResponse segundoPaso(SegundoPasoRequest request) {
        // Mismo mensaje para todo lo que no sea el código: caducado,
        // manipulado, de otra cuenta, de una contraseña que ya no es la suya.
        BusinessException otraVez = new BusinessException(
                "Han pasado más de cinco minutos o algo ha cambiado en la cuenta. Vuelve a entrar con tu contraseña.",
                HttpStatus.UNAUTHORIZED);
        JwtService.Desafio desafio = jwtService.leerDesafio(request.desafio()).orElseThrow(() -> otraVez);
        User user = userRepository.findById(desafio.usuarioId())
                .filter(User::isActivo)
                .filter(usuario -> huellaDe(usuario).equals(desafio.huella()))
                .orElseThrow(() -> otraVez);
        cicloDeLaEmpresa.exigirActiva(user.getEmpresa());

        if (!segundoFactor.verificarAlEntrar(user, request.codigo())) {
            throw new BusinessException("El código no es correcto. Comprueba que el móvil tiene bien la hora, "
                    + "o usa uno de tus códigos de recuperación.", HttpStatus.UNAUTHORIZED);
        }
        log.info("Login correcto con segundo factor: {}", user.getId());
        return buildAuthResponse(user, origenDe(request.origen()), true);
    }

    /**
     * Lo que el desafío tiene que seguir cumpliendo al canjearlo: la misma
     * contraseña y el mismo segundo factor que cuando se emitió. Si en esos
     * cinco minutos se cambia la contraseña (con «He olvidado mi contraseña»)
     * o se quita y se vuelve a poner el segundo factor, el desafío deja de
     * valer. Son 64 bits del hash de las dos cosas: no dice nada de ninguna.
     */
    static String huellaDe(User user) {
        return hashDe(user.getContrasena() + "|" + user.getSegundoFactorDesde()).substring(0, 16);
    }

    /**
     * El origen que declara el cliente, o ANDROID si no dice nada.
     *
     * Un valor desconocido tampoco es un error: se trata como "no lo ha
     * dicho". Rechazar el login por un campo que solo decide una caducidad
     * sería dejar fuera a alguien por algo que no importa.
     */
    private RefreshToken.Origen origenDe(String declarado) {
        if (declarado == null || declarado.isBlank()) {
            return RefreshToken.Origen.ANDROID;
        }
        try {
            return RefreshToken.Origen.valueOf(declarado.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException desconocido) {
            // Limpio y corto: lo escribe quien llama, y con un salto de línea
            // dentro podría fabricar líneas de registro que parezcan nuestras.
            String limpio = declarado.replaceAll("\\p{Cntrl}", "?");
            log.warn("Origen de login desconocido: '{}'. Se trata como ANDROID.",
                    limpio.length() > 40 ? limpio.substring(0, 40) + "…" : limpio);
            return RefreshToken.Origen.ANDROID;
        }
    }

    /**
     * Renueva el access token y ROTA el refresh (Fase A11).
     *
     * Devuelve siempre un refresh nuevo: el que se presenta queda marcado y no
     * vuelve a valer. Quien llame tiene que guardarlo, o su siguiente
     * renovación fallará.
     *
     * Si el token que llega ya estaba rotado, hay dos clientes usando la misma
     * cadena y solo uno puede ser el legítimo. No hay forma de distinguirlos,
     * así que se cierra la familia entera y los dos vuelven al login. Es
     * ruidoso a propósito: un robo silencioso dura treinta días; esto se nota
     * el mismo día.
     *
     * <b>{@code noRollbackFor} es lo más importante de este método</b>, igual
     * que en {@code AccessCodeServiceImpl#confirmar} y por la misma razón:
     * revocar la familia ES un cambio que tiene que persistir, y además se
     * lanza. Con un {@code @Transactional} normal, la excepción deshace el
     * UPDATE y la protección no protege nada -- el token robado queda revocado
     * durante el tiempo que dura la transacción y vuelve a estar vivo al
     * salir.
     *
     * No es teoría: se verificó contra producción. Rotar, reutilizar el token
     * viejo y volver a usar el nuevo devolvía 200 cuando tenía que devolver
     * 401. Los tests unitarios no lo veían porque con mocks no hay transacción
     * que deshacer.
     */
    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthenticationResponse refreshAccessToken(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hashDe(refreshToken))
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido o caducado."));

        if (stored.estaRotado()) {
            int cerradas = refreshTokenRepository.revocarLaFamilia(stored.getFamilia(), Instant.now());
            // warn y no error: es un escenario previsto, y probablemente el
            // que más interesa ver en Sentry de todo el módulo de sesiones.
            log.warn("Refresh token REUTILIZADO por {}: esa cadena ya se había rotado. "
                    + "Cerradas {} sesiones de la familia.", stored.getUsuario().getId(), cerradas);
            throw new BadCredentialsException("Refresh token inválido o caducado.");
        }

        if (!stored.estaVivo()) {
            throw new BadCredentialsException("Refresh token inválido o caducado.");
        }

        User user = stored.getUsuario();
        // Hasta el 17/10/2026 se renovaba sin mirarlo, y a quien daban de baja
        // le seguían saliendo access tokens durante los treinta días de su
        // refresh (plan J). Los filtros lo paraban en cada petición, pero un
        // token emitido para una cuenta de baja no debería existir.
        if (!user.isActivo() || (user.getEmpresa() != null && !user.getEmpresa().estaActiva())) {
            // Ni si su empresa ya no está activa (ADR 046): al suspenderla se
            // revocan sus sesiones, pero esto no depende de que se hiciera.
            throw new BadCredentialsException("Refresh token inválido o caducado.");
        }
        // El sucesor conserva familia y origen: es la misma sesión, con otro
        // token.
        TokenEmitido sucesor = issueRefreshToken(user, stored.getOrigen(), stored.getFamilia(),
                stored.isConSegundoFactor());

        stored.setRotadoEn(Instant.now());
        stored.setSustituidoPor(sucesor.fila());
        refreshTokenRepository.save(stored);

        String newAccessToken = jwtService.generateToken(operadores.principalDe(user), stored.getFamilia(),
                stored.isConSegundoFactor());
        log.info("Access token renovado para {}", user.getId());
        return new AuthenticationResponse(newAccessToken, sucesor.token(), user.getNombre(), user.getRol(),
                operadores.authoritiesDe(user), user.zona().getId(), condicionesPorAceptar(user),
                segundoFactorPorActivar(user, stored.isConSegundoFactor()));
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hashDe(refreshToken)).ifPresent(stored -> {
            // Se revoca la FAMILIA entera y no solo este token: cerrar sesion
            // tiene que cerrar la sesion, no el eslabon que el cliente tuviera
            // a mano. Si cayera solo este, un token anterior de la cadena
            // seguiria pudiendo reabrirla.
            refreshTokenRepository.revocarLaFamilia(stored.getFamilia(), Instant.now());
            log.info("Sesión cerrada para {}", stored.getUsuario().getId());
        });
        // Si el token no existe, no pasa nada -- logout es idempotente y
        // no revela si un token era válido o no.
    }

    @Override
    @Transactional
    public AuthenticationResponse abrirSesion(long usuarioId, RefreshToken.Origen origen) {
        User user = userRepository.findById(usuarioId)
                .filter(User::isActivo)
                .orElseThrow(() -> new BadCredentialsException("La cuenta no existe o está dada de baja."));
        cicloDeLaEmpresa.exigirActiva(user.getEmpresa());
        // Solo la usa el SSO, y entrar con Google o Microsoft cuenta como
        // segundo factor: lo pone el proveedor (ADR 051).
        return buildAuthResponse(user, origen, true);
    }

    @Override
    public java.util.Optional<Long> usuarioDelRefresh(String refreshToken) {
        return refreshTokenRepository.findByTokenHash(hashDe(refreshToken))
                .filter(RefreshToken::estaVivo)
                .map(token -> token.getUsuario().getId());
    }

    @Override
    @Transactional
    public void createEmployee(CreateEmployeeRequest request, User manager) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("El email ya está registrado.");
        }

        Company managerCompany = manager.getEmpresa();

        User newEmployee = User.builder()
                .nombre(request.nombre())
                .apellidos(request.apellidos())
                .email(request.email())
                .contrasena(contrasenaInutilizable())
                .rol(Role.EMPLEADO)
                .empresa(managerCompany)
                .build();

        User savedEmployee = userRepository.save(newEmployee);

        // El código de acceso, en el momento y DENTRO de esta transacción:
        // si el correo no sale, lanza y el alta se deshace (ver AccessCodeService).
        accessCodeService.emitirCodigoDeAlta(savedEmployee, managerCompany.getNombre());

        // Aviso de bienvenida dentro de la aplicación (Fase A). El correo de
        // bienvenida de la Fase 10 ya no existe: es el del código de alta.
        eventPublisher.publishEvent(
                new NotificationEvents.EmployeeCreated(savedEmployee, managerCompany.getNombre()));

        log.info("Gestor {} ha creado al empleado {}", manager.getId(), newEmployee.getId());
    }

    @Override
    @Transactional
    public void createManager(CreateManagerRequest request, User admin) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("El email ya está registrado.");
        }

        Company company = admin.getEmpresa();

        User newManager = User.builder()
                .nombre(request.nombre())
                .apellidos(request.apellidos())
                .email(request.email())
                .contrasena(contrasenaInutilizable())
                .rol(Role.GESTOR)
                .empresa(company)
                .build();

        User savedManager = userRepository.save(newManager);
        accessCodeService.emitirCodigoDeAlta(savedManager, company.getNombre());
        log.info("Administrador {} ha creado al gestor {}", admin.getId(), newManager.getId());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void verificarContrasena(User usuario, String contrasena) {
        if (!contrasenaCorrecta(usuario, contrasena)) {
            throw new BadCredentialsException("Credenciales incorrectas.");
        }
    }

    /**
     * La contraseña de quien ya tiene sesión, con el límite de intentos de su
     * cuenta y por turnos, como el login (revisión de seguridad del
     * 17/10/2026, plan J). Hasta entonces, quien tuviera un access token
     * ajeno --vive quince minutos-- podía probar contraseñas de esa persona
     * aquí sin ningún freno, y cada prueba era un BCrypt fuera de turno.
     *
     * Sin transacción, por lo del login: ver {@link #login}.
     */
    private boolean contrasenaCorrecta(User usuario, String contrasena) {
        limitadorPorCuenta.comprobar(usuario.getEmail());
        boolean correcta = turnoDeContrasena.conTurno(
                () -> passwordEncoder.matches(contrasena, usuario.getContrasena()));
        if (correcta) {
            limitadorPorCuenta.acierto(usuario.getEmail());
        } else {
            limitadorPorCuenta.fallo(usuario.getEmail());
        }
        return correcta;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void changePassword(ChangePasswordRequest request, User user, Optional<UUID> sesionActual) {
        if (!contrasenaCorrecta(user, request.contrasenaAntigua())) {
            log.warn("Intento de cambio de contraseña con contraseña antigua incorrecta: {}", user.getId());
            throw new BusinessException("La contraseña antigua no es correcta.", HttpStatus.BAD_REQUEST);
        }
        String nueva = turnoDeContrasena.conTurno(() -> passwordEncoder.encode(request.contrasenaNueva()));

        int cerradas = escritura.execute(estado -> {
            user.setContrasena(nueva);
            userRepository.save(user);

            // Las demás sesiones se cierran, como al elegir contraseña con un
            // código (revisión de seguridad del 1/10/2026, ADR 034): quien cambia
            // la contraseña porque sospecha que se la han robado tiene que poder
            // echar al que entró con ella. La suya sigue abierta. Un access token
            // de antes del ADR 034 no dice su sesión: entonces se cierran todas.
            Instant ahora = Instant.now();
            return sesionActual
                    .map(familia -> refreshTokenRepository.revocarTodasLasDeMenos(user, familia, ahora))
                    .orElseGet(() -> refreshTokenRepository.revocarTodasLasDe(user, ahora));
        });
        log.info("Contraseña cambiada: {} ({} sesiones cerradas)", user.getId(), cerradas);
    }

    @Override
    @Transactional
    public void cerrarTodasLasSesiones(User user) {
        // Es la misma revocación que hace elegir contraseña con un código
        // (ver AccessCodeServiceImpl), y por el mismo motivo: si alguien
        // pudo entrar con tu cuenta, lo que hay que cortar es su capacidad
        // de RENOVAR el acceso, que es lo que la hace duradera.
        Instant ahora = Instant.now();
        int cerradas = refreshTokenRepository.revocarTodasLasDe(user, ahora);
        // Y los access tokens ya emitidos, que hasta octubre de 2026 seguían
        // valiendo sus quince minutos (ADR 051): ver JwtAuthenticationFilter.
        userRepository.invalidarAccesosAnterioresA(user.getId(), ahora);
        log.info("{} ha cerrado la sesión en todos sus dispositivos ({} revocadas)",
                user.getId(), cerradas);
    }

    @Override
    @Transactional
    public void setEmployeeActive(long employeeId, boolean activo, User actingManager) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        if (employee.getEmpresa().getId() != actingManager.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes gestionar empleados de otra empresa.");
        }
        // Revisión de seguridad del 17/10/2026 (plan J). Hasta entonces RRHH
        // podía dar de baja a un ADMIN, y un ADMIN a sí mismo: con uno solo, la
        // empresa se quedaba sin nadie que la administrara y sin forma de
        // arreglarlo desde dentro. A uno mismo no se le da de baja (que lo haga
        // otro ADMIN), así que siempre queda al menos el que actúa.
        if (employee.getId() == actingManager.getId()) {
            throw new BusinessException("No puedes darte de baja ni de alta a ti mismo. Pídeselo a otro administrador.",
                    HttpStatus.FORBIDDEN);
        }
        if (employee.getRol().ordinal() > actingManager.getRol().ordinal()) {
            throw new BusinessException("No puedes dar de baja ni de alta a alguien con un rol por encima del tuyo.",
                    HttpStatus.FORBIDDEN);
        }

        employee.setActivo(activo);
        employee.setFechaBaja(activo ? null : Instant.now());
        userRepository.save(employee);
        if (!activo) {
            // Sus sesiones también: renovar ya lo impide (refreshAccessToken),
            // pero un refresh vivo de alguien de baja no tiene por qué existir.
            refreshTokenRepository.revocarTodasLasDe(employee, Instant.now());
            if (companyRepository.quitarDelCanal(employee.getId()) > 0) {
                log.warn("{} era Responsable del canal de denuncias de la empresa {}: vuelven a instruir los ADMIN.",
                        employee.getId(), employee.getEmpresa().getId());
            }
        }
        log.info("{} {} por {}", activo ? "Reactivado" : "Dado de baja", employee.getId(), actingManager.getId());
    }

    /**
     * La contraseña de una cuenta recién creada, hasta que su dueño elija
     * la suya con el código de alta.
     *
     * No puede ir vacía (la columna es NOT NULL) ni ser una que alguien
     * conozca: es el hash de 122 bits de azar que no se guardan en ningún
     * sitio, así que no existe contraseña con la que entrar.
     */
    private String contrasenaInutilizable() {
        return passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * @param conSegundoFactor si esta sesión ha pasado por un segundo factor:
     *   un código, o Google/Microsoft (ADR 051). Lo lleva el refresh, y de él
     *   cada access token que se emita en esta sesión.
     */
    private AuthenticationResponse buildAuthResponse(User user, RefreshToken.Origen origen, boolean conSegundoFactor) {
        // Familia nueva en cada login: así cerrar una sesión comprometida no
        // arrastra a las demás de esa persona. El access token la lleva
        // (claim sid, ADR 034) para saber, al cambiar la contraseña, cuál es
        // la sesión que NO hay que cerrar.
        UUID familia = UUID.randomUUID();
        String accessToken = jwtService.generateToken(operadores.principalDe(user), familia, conSegundoFactor);
        TokenEmitido refreshToken = issueRefreshToken(user, origen, familia, conSegundoFactor);
        return new AuthenticationResponse(accessToken, refreshToken.token(), user.getNombre(), user.getRol(),
                operadores.authoritiesDe(user), user.zona().getId(), condicionesPorAceptar(user),
                segundoFactorPorActivar(user, conSegundoFactor));
    }

    /**
     * Si a esta persona se le exige segundo factor y esta sesión no ha pasado
     * por él (ADR 051): solo podrá activarlo (SegundoFactorObligatorioFilter),
     * y el cliente la lleva directamente a hacerlo.
     */
    private boolean segundoFactorPorActivar(User user, boolean conSegundoFactor) {
        return !conSegundoFactor && segundoFactorObligatorio.exigido(user);
    }

    /**
     * Si a esta persona hay que pedirle que acepte las condiciones en vigor
     * (ADR 046): solo a un ADMIN, que es quien compromete a la empresa, y solo
     * si su empresa aceptó otra versión o ninguna (las de antes de la V43).
     */
    private boolean condicionesPorAceptar(User user) {
        return user.getRol() == Role.ADMIN
                && user.getEmpresa() != null
                && !servicio.version().equals(user.getEmpresa().getCondicionesVersion());
    }

    /**
     * Un token recién emitido: su valor en claro y la fila que lo representa.
     *
     * Van juntos porque quien rota necesita las dos cosas --el valor para
     * devolverlo y la fila para enlazarla como sucesor-- y releer la fila por
     * su hash justo después de guardarla no funciona: dentro de la misma
     * transacción el INSERT todavía no se ha volcado, así que la consulta no
     * lo encuentra.
     */
    private record TokenEmitido(String token, RefreshToken fila) {
    }

    /**
     * Emite un refresh token. El valor EN CLARO solo existe en lo que
     * devuelve: en la base queda su sha256.
     *
     * La vida la decide el origen: un móvil dura treinta días y un navegador
     * lo que diga {@code web-session.duracion}, siete días si no se dice otra
     * cosa (ver {@link RefreshToken.Origen}). La propiedad
     * {@code refresh-expiration} sigue marcando el techo, para que bajarla
     * siga sirviendo de freno global.
     */
    private TokenEmitido issueRefreshToken(User user, RefreshToken.Origen origen, UUID familia,
            boolean conSegundoFactor) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Duration porOrigen = origen == RefreshToken.Origen.WEB ? vidaDeLaSesionWeb : origen.duracion();
        Duration techo = Duration.ofMillis(refreshExpirationMillis);
        Duration vida = porOrigen.compareTo(techo) <= 0 ? porOrigen : techo;

        RefreshToken fila = refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hashDe(token))
                .usuario(user)
                .familia(familia)
                .origen(origen)
                .conSegundoFactor(conSegundoFactor)
                .creadoEn(now)
                .expiraEn(now.plus(vida))
                .build());
        return new TokenEmitido(token, fila);
    }

    /**
     * El sha256 de un token, en hexadecimal.
     *
     * Ver {@link RefreshToken#getTokenHash()} para por qué SHA-256 y no
     * BCrypt: esto no es una contraseña elegida por nadie sino un UUID
     * aleatorio, y lo que se busca es que la base no contenga nada
     * reutilizable, no resistir un diccionario.
     */
    static String hashDe(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 está garantizado en cualquier JVM estándar.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
