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
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.JwtService;
import com.nxtime.nxtime.service.AuthService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementación de la lógica de negocio para Usuarios y Seguridad.
 *
 * Migración 1:1 de ServicioAutenticacionImpl.kt: se conservan los mismos
 * defectos conocidos (registerManager no comprueba existsByEmail antes
 * de guardar, sin @Transactional, longitud mínima de contraseña solo
 * validada en changePassword...). Se corrigen en la Fase 2.
 */
@Service
public class AuthServiceImpl implements AuthService {

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

    @Override
    public AuthenticationResponse registerManager(RegisterManagerRequest request) {
        if (companyRepository.findByNombre(request.nombreEmpresa()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La empresa ya existe. Solicita acceso al administrador.");
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
        String jwtToken = jwtService.generateToken(savedUser);

        return new AuthenticationResponse(jwtToken, savedUser.getNombre(), savedUser.getRol());
    }

    @Override
    public AuthenticationResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.contrasena()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado con email: " + request.email()));

        String jwtToken = jwtService.generateToken(user);

        return new AuthenticationResponse(jwtToken, user.getNombre(), user.getRol());
    }

    @Override
    public void createEmployee(CreateEmployeeRequest request, User manager) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El email ya está registrado.");
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
    }

    @Override
    public void createManager(CreateManagerRequest request, User admin) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El email ya está registrado.");
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
    }

    @Override
    public void changePassword(ChangePasswordRequest request, User user) {
        if (!passwordEncoder.matches(request.contrasenaAntigua(), user.getContrasena())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña antigua no es correcta.");
        }

        if (request.contrasenaNueva().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe tener al menos 6 caracteres.");
        }

        user.setContrasena(passwordEncoder.encode(request.contrasenaNueva()));
        userRepository.save(user);
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
