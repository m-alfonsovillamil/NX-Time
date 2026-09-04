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
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.AuthService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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

    @Value("${application.security.jwt.refresh-expiration}")
    private long refreshExpirationMillis;

    public AuthServiceImpl(
            UserRepository userRepository,
            CompanyRepository companyRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            ApplicationEventPublisher eventPublisher
    ) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.eventPublisher = eventPublisher;
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
                .nombre(request.nombreGestor())
                .email(request.email())
                .contrasena(passwordEncoder.encode(request.password()))
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
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.contrasena()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + request.email()));

        log.info("Login correcto: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthenticationResponse refreshAccessToken(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido o caducado."));

        if (!stored.estaVivo()) {
            throw new BadCredentialsException("Refresh token inválido o caducado.");
        }

        User user = stored.getUsuario();
        String newAccessToken = jwtService.generateToken(new SecurityUser(user));

        log.info("Access token renovado para {}", user.getEmail());
        return new AuthenticationResponse(newAccessToken, stored.getToken(), user.getNombre(), user.getRol());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(stored -> {
            stored.setRevocado(true);
            refreshTokenRepository.save(stored);
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
                .email(request.email())
                .contrasena(passwordEncoder.encode(request.contrasena()))
                .rol(Role.EMPLEADO)
                .empresa(managerCompany)
                .build();

        User savedEmployee = userRepository.save(newEmployee);
        // Correo de bienvenida (Fase 10). Sin la contraseña dentro: la
        // comunica quien da el alta por otro canal (ver la plantilla).
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
                .email(request.email())
                .contrasena(passwordEncoder.encode(request.contrasena()))
                .rol(Role.GESTOR)
                .empresa(company)
                .build();

        userRepository.save(newManager);
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

    private AuthenticationResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(new SecurityUser(user));
        String refreshToken = issueRefreshToken(user);
        return new AuthenticationResponse(accessToken, refreshToken, user.getNombre(), user.getRol());
    }

    private String issueRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .usuario(user)
                .creadoEn(now)
                .expiraEn(now.plus(refreshExpirationMillis, ChronoUnit.MILLIS))
                .build();

        refreshTokenRepository.save(refreshToken);
        return token;
    }
}
