package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.RefreshToken;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.ChangePasswordRequest;
import com.nxtime.nxtime.dto.CreateEmployeeRequest;
import com.nxtime.nxtime.dto.CreateManagerRequest;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.RefreshTokenRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.JwtService;
import com.nxtime.nxtime.security.LimitadorDeIntentosPorCuenta;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.AccessCodeService;
import com.nxtime.nxtime.service.AuthService;
import java.time.Instant;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher;
    private final AccessCodeService accessCodeService;
    private final LimitadorDeIntentosPorCuenta limitadorPorCuenta;

    @Value("${application.security.jwt.refresh-expiration}")
    private long refreshExpirationMillis;

    public AuthServiceImpl(
            UserRepository userRepository,
            CompanyRepository companyRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            ApplicationEventPublisher eventPublisher,
            AccessCodeService accessCodeService,
            LimitadorDeIntentosPorCuenta limitadorPorCuenta
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
    }

    // Desde la Fase 3 (PostgreSQL + IDENTITY) esto es una transacción
    // normal: los dos save() (Company y User) son atómicos de verdad.
    @Override
    @Transactional
    public AuthenticationResponse registerManager(RegisterManagerRequest request) {
        if (companyRepository.findByNombre(request.nombreEmpresa()).isPresent()) {
            throw new BusinessException("La empresa ya existe. Solicita acceso al administrador.");
        }

        Company company = companyRepository.save(Company.builder().nombre(request.nombreEmpresa()).build());

        User user = User.builder()
                .nombre(request.nombre())
                .apellidos(request.apellidos())
                .email(request.email())
                .contrasena(passwordEncoder.encode(request.contrasena()))
                .rol(Role.ADMIN)
                .empresa(company)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Nueva empresa registrada: '{}' con administrador {}", company.getNombre(), savedUser.getEmail());
        return buildAuthResponse(savedUser);
    }

    // @Transactional de escritura: desde la Fase 4, login() también
    // persiste un RefreshToken (ver buildAuthResponse). Sin esto, la
    // transacción de solo lectura heredada de la clase rechaza el
    // INSERT con "cannot execute INSERT in a read-only transaction".
    @Override
    @Transactional
    public AuthenticationResponse login(LoginRequest request) {
        // Además del límite por IP (LoginRateLimitFilter), un límite por
        // CUENTA: la IP sale de una cabecera y depende de los proxies que
        // haya delante, el correo al que se intenta entrar no. Es lo que
        // frena probar contraseñas de alguien desde muchos sitios a la vez.
        limitadorPorCuenta.comprobar(request.email());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.contrasena()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + request.email()));

        log.info("Login correcto: {}", user.getEmail());
        return buildAuthResponse(user, origenDe(request.origen()));
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
            log.warn("Origen de login desconocido: '{}'. Se trata como ANDROID.", declarado);
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
                    + "Cerradas {} sesiones de la familia.", stored.getUsuario().getEmail(), cerradas);
            throw new BadCredentialsException("Refresh token inválido o caducado.");
        }

        if (!stored.estaVivo()) {
            throw new BadCredentialsException("Refresh token inválido o caducado.");
        }

        User user = stored.getUsuario();
        // El sucesor conserva familia y origen: es la misma sesión, con otro
        // token.
        TokenEmitido sucesor = issueRefreshToken(user, stored.getOrigen(), stored.getFamilia());

        stored.setRotadoEn(Instant.now());
        stored.setSustituidoPor(sucesor.fila());
        refreshTokenRepository.save(stored);

        String newAccessToken = jwtService.generateToken(new SecurityUser(user));
        log.info("Access token renovado para {}", user.getEmail());
        return new AuthenticationResponse(newAccessToken, sucesor.token(), user.getNombre(), user.getRol());
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
            log.info("Sesión cerrada para {}", stored.getUsuario().getEmail());
        });
        // Si el token no existe, no pasa nada -- logout es idempotente y
        // no revela si un token era válido o no.
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

        log.info("Gestor {} ha creado al empleado {}", manager.getEmail(), newEmployee.getEmail());
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
        log.info("Administrador {} ha creado al gestor {}", admin.getEmail(), newManager.getEmail());
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request, User user) {
        if (!passwordEncoder.matches(request.contrasenaAntigua(), user.getContrasena())) {
            log.warn("Intento de cambio de contraseña con contraseña antigua incorrecta: {}", user.getEmail());
            throw new BusinessException("La contraseña antigua no es correcta.", HttpStatus.BAD_REQUEST);
        }

        user.setContrasena(passwordEncoder.encode(request.contrasenaNueva()));
        userRepository.save(user);
        log.info("Contraseña cambiada: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void cerrarTodasLasSesiones(User user) {
        // Es la misma revocación que hace elegir contraseña con un código
        // (ver AccessCodeServiceImpl), y por el mismo motivo: si alguien
        // pudo entrar con tu cuenta, lo que hay que cortar es su capacidad
        // de RENOVAR el acceso, que es lo que la hace duradera.
        int cerradas = refreshTokenRepository.revocarTodasLasDe(user, Instant.now());
        log.info("{} ha cerrado la sesión en todos sus dispositivos ({} revocadas)",
                user.getEmail(), cerradas);
    }

    @Override
    @Transactional
    public void setEmployeeActive(long employeeId, boolean activo, User actingManager) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        if (employee.getEmpresa().getId() != actingManager.getEmpresa().getId()) {
            throw new TenantAccessException("No puedes gestionar empleados de otra empresa.");
        }

        employee.setActivo(activo);
        employee.setFechaBaja(activo ? null : Instant.now());
        userRepository.save(employee);
        log.info("{} {} por {}", activo ? "Reactivado" : "Dado de baja", employee.getEmail(), actingManager.getEmail());
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

    private AuthenticationResponse buildAuthResponse(User user) {
        return buildAuthResponse(user, RefreshToken.Origen.ANDROID);
    }

    private AuthenticationResponse buildAuthResponse(User user, RefreshToken.Origen origen) {
        String accessToken = jwtService.generateToken(new SecurityUser(user));
        // Familia nueva en cada login: así cerrar una sesión comprometida no
        // arrastra a las demás de esa persona.
        TokenEmitido refreshToken = issueRefreshToken(user, origen, UUID.randomUUID());
        return new AuthenticationResponse(accessToken, refreshToken.token(), user.getNombre(), user.getRol());
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
     * La vida la decide el origen y no la configuración: un navegador dura
     * doce horas y un móvil treinta días (ver {@link RefreshToken.Origen}). La
     * propiedad {@code refresh-expiration} sigue marcando el techo, para que
     * bajarla siga sirviendo de freno global.
     */
    private TokenEmitido issueRefreshToken(User user, RefreshToken.Origen origen, UUID familia) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Duration porOrigen = origen.duracion();
        Duration techo = Duration.ofMillis(refreshExpirationMillis);
        Duration vida = porOrigen.compareTo(techo) <= 0 ? porOrigen : techo;

        RefreshToken fila = refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hashDe(token))
                .usuario(user)
                .familia(familia)
                .origen(origen)
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
