package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.ChangePasswordRequest;
import com.nxtime.nxtime.dto.CreateEmployeeRequest;
import com.nxtime.nxtime.dto.CreateManagerRequest;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import com.nxtime.nxtime.dto.SimpleEmployeeDTO;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.JwtService;
import com.nxtime.nxtime.security.SecurityUser;
import com.nxtime.nxtime.service.AuthService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de la lógica de negocio para Usuarios y Seguridad.
 */
@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthServiceImpl(
            UserRepository userRepository,
            CompanyRepository companyRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    // NOTA: a diferencia del resto de métodos de escritura, este va con
    // Propagation.NOT_SUPPORTED (explícitamente SIN transacción),
    // pisando el @Transactional(readOnly = true) de la clase. Hace dos
    // save() (Company y User) con GenerationType.TABLE cada uno: el
    // generador de IDs necesita, para cada entidad, su propia conexión
    // "aislada" para confirmar el siguiente id de inmediato. Envolver
    // ambos save() en una única transacción hace que esa conexión
    // aislada del segundo generador choque con el lock de escritura que
    // la transacción exterior ya mantiene abierto sobre el mismo
    // fichero SQLite (solo admite un escritor a la vez) -- un
    // interbloqueo real, verificado empíricamente, no una suposición.
    // Por eso este método concreto mantiene el comportamiento no
    // atómico de la Fase 1 (si el segundo save fallara, quedaría una
    // empresa huérfana) hasta la Fase 3, que migra a PostgreSQL +
    // IDENTITY y elimina el problema de raíz.
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthenticationResponse registerManager(RegisterManagerRequest request) {
        if (companyRepository.findByNombre(request.nombreEmpresa()).isPresent()) {
            throw new BusinessException("La empresa ya existe. Solicita acceso al administrador.");
        }

        Company company = companyRepository.save(Company.builder().nombre(request.nombreEmpresa()).build());

        User user = User.builder()
                .nombre(request.nombreGestor())
                .email(request.email())
                .contrasena(passwordEncoder.encode(request.password()))
                .rol(Role.GESTOR)
                .empresa(company)
                .build();

        User savedUser = userRepository.save(user);
        String jwtToken = jwtService.generateToken(new SecurityUser(savedUser));

        log.info("Nueva empresa registrada: '{}' con gestor {}", company.getNombre(), savedUser.getEmail());
        return new AuthenticationResponse(jwtToken, savedUser.getNombre(), savedUser.getRol());
    }

    @Override
    public AuthenticationResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.contrasena()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + request.email()));

        String jwtToken = jwtService.generateToken(new SecurityUser(user));

        log.info("Login correcto: {}", user.getEmail());
        return new AuthenticationResponse(jwtToken, user.getNombre(), user.getRol());
    }

    // Sin transacción, igual que registerManager (ver el comentario
    // detallado ahí): crea un User nuevo con GenerationType.TABLE, y
    // CUALQUIER @Transactional alrededor de un insert con TABLE se
    // bloquea contra la conexión aislada del propio generador de IDs en
    // SQLite -- no hace falta que haya un segundo save() para que pase,
    // basta con uno solo (verificado empíricamente: al principio se
    // pensó que el problema era solo con dos saves, pero un único
    // insert transaccional ya lo reproduce).
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

        userRepository.save(newEmployee);
        log.info("Gestor {} ha creado al empleado {}", manager.getEmail(), newEmployee.getEmail());
    }

    // Sin transacción -- mismo motivo que createEmployee.
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
        log.info("Gestor {} ha creado a otro gestor {}", admin.getEmail(), newManager.getEmail());
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
    public List<SimpleEmployeeDTO> getMyEmployees(User manager) {
        Company managerCompany = manager.getEmpresa();
        List<User> employees = userRepository.findByEmpresaAndRol(managerCompany, Role.EMPLEADO);

        return employees.stream()
                .map(employee -> new SimpleEmployeeDTO(employee.getId(), employee.getNombre(), employee.getEmail()))
                .toList();
    }
}
