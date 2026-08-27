package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.RefreshTokenRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unitarios (Mockito) de autenticación, refresh tokens y gestión de
 * cuentas. {@code refreshExpirationMillis} se inyecta con
 * {@link ReflectionTestUtils} porque fuera de un contexto Spring
 * {@code @Value} nunca se resuelve.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                userRepository, companyRepository, refreshTokenRepository, passwordEncoder, jwtService,
                authenticationManager);
        ReflectionTestUtils.setField(service, "refreshExpirationMillis", 2_592_000_000L);
        // lenient: solo los tests que emiten tokens de verdad llegan a estas líneas.
        lenient().when(jwtService.generateToken(any())).thenReturn("access-token");
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- registerManager ----

    @Test
    @DisplayName("registerManager con empresa nueva crea la empresa y un ADMIN (no un GESTOR)")
    void registerManager_empresaNueva_creaEmpresaYAdmin() {
        RegisterManagerRequest request =
                new RegisterManagerRequest("Empresa Nueva SL", "Ada", "ada@nxtime.test", "password123");
        when(companyRepository.findByNombre(request.nombreEmpresa())).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class)))
                .thenReturn(Company.builder().id(1L).nombre(request.nombreEmpresa()).build());
        when(passwordEncoder.encode(request.password())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthenticationResponse response = service.registerManager(request);

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.rol()).isEqualTo(Role.ADMIN);
        assertThat(response.refreshToken()).isNotBlank();
        verify(userRepository).save(argThat(u -> u.getRol() == Role.ADMIN));
    }

    @Test
    @DisplayName("registerManager con una empresa ya existente lanza BusinessException")
    void registerManager_empresaYaExiste_lanzaBusinessException() {
        RegisterManagerRequest request =
                new RegisterManagerRequest("Empresa Repetida SL", "Ada", "ada@nxtime.test", "password123");
        when(companyRepository.findByNombre(request.nombreEmpresa()))
                .thenReturn(Optional.of(Company.builder().id(1L).nombre(request.nombreEmpresa()).build()));

        assertThatThrownBy(() -> service.registerManager(request)).isInstanceOf(BusinessException.class);
        verify(userRepository, never()).save(any());
    }

    // ---- login ----

    @Test
    @DisplayName("login con credenciales válidas autentica y emite access token + refresh token")
    void login_credencialesValidas_emiteTokens() {
        Company empresa = Company.builder().id(1L).nombre("Empresa").build();
        User user = User.builder().id(1L).email("gestor@nxtime.test").rol(Role.GESTOR).empresa(empresa).build();
        LoginRequest request = new LoginRequest(user.getEmail(), "password123");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        AuthenticationResponse response = service.login(request);

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.rol()).isEqualTo(Role.GESTOR);
        verify(authenticationManager).authenticate(any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    // ---- refreshAccessToken ----

    @Test
    @DisplayName("refreshAccessToken con un token vivo emite un access token nuevo y reutiliza el mismo refresh token")
    void refreshAccessToken_tokenVivo_emiteAccessTokenNuevo() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").rol(Role.EMPLEADO).build();
        RefreshToken stored = RefreshToken.builder().id(1L).token("refresh-abc").usuario(user)
                .expiraEn(Instant.now().plusSeconds(3600)).revocado(false).build();
        when(refreshTokenRepository.findByToken("refresh-abc")).thenReturn(Optional.of(stored));

        AuthenticationResponse response = service.refreshAccessToken("refresh-abc");

        assertThat(response.token()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-abc");
    }

    @Test
    @DisplayName("refreshAccessToken con un token que no existe lanza BadCredentialsException")
    void refreshAccessToken_tokenInexistente_lanzaBadCredentialsException() {
        when(refreshTokenRepository.findByToken("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refreshAccessToken("no-existe"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("refreshAccessToken con un token revocado lanza BadCredentialsException")
    void refreshAccessToken_tokenRevocado_lanzaBadCredentialsException() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").build();
        RefreshToken stored = RefreshToken.builder().id(1L).token("refresh-abc").usuario(user)
                .expiraEn(Instant.now().plusSeconds(3600)).revocado(true).build();
        when(refreshTokenRepository.findByToken("refresh-abc")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refreshAccessToken("refresh-abc"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("refreshAccessToken con un token caducado lanza BadCredentialsException")
    void refreshAccessToken_tokenCaducado_lanzaBadCredentialsException() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").build();
        RefreshToken stored = RefreshToken.builder().id(1L).token("refresh-abc").usuario(user)
                .expiraEn(Instant.now().minusSeconds(1)).revocado(false).build();
        when(refreshTokenRepository.findByToken("refresh-abc")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refreshAccessToken("refresh-abc"))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ---- logout ----

    @Test
    @DisplayName("logout de un token existente lo revoca")
    void logout_tokenExistente_loRevoca() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").build();
        RefreshToken stored = RefreshToken.builder().id(1L).token("refresh-abc").usuario(user).revocado(false).build();
        when(refreshTokenRepository.findByToken("refresh-abc")).thenReturn(Optional.of(stored));

        service.logout("refresh-abc");

        assertThat(stored.isRevocado()).isTrue();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    @DisplayName("logout de un token que no existe es idempotente (no lanza, no revela nada)")
    void logout_tokenInexistente_esIdempotente() {
        when(refreshTokenRepository.findByToken("no-existe")).thenReturn(Optional.empty());

        service.logout("no-existe");

        verify(refreshTokenRepository, never()).save(any());
    }

    // ---- createEmployee / createManager ----

    @Test
    @DisplayName("createEmployee con email ya registrado lanza BusinessException")
    void createEmployee_emailYaRegistrado_lanzaBusinessException() {
        User manager = User.builder().id(1L).empresa(Company.builder().id(1L).build()).build();
        CreateEmployeeRequest request = new CreateEmployeeRequest("Nuevo", "nuevo@nxtime.test", "password1");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployee(request, manager)).isInstanceOf(BusinessException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("createEmployee con email libre crea al empleado en la empresa del gestor")
    void createEmployee_emailLibre_creaEmpleado() {
        Company empresa = Company.builder().id(1L).build();
        User manager = User.builder().id(1L).empresa(empresa).build();
        CreateEmployeeRequest request = new CreateEmployeeRequest("Nuevo", "nuevo@nxtime.test", "password1");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.contrasena())).thenReturn("hash");

        service.createEmployee(request, manager);

        verify(userRepository).save(argThat(u -> u.getRol() == Role.EMPLEADO && u.getEmpresa() == empresa));
    }

    @Test
    @DisplayName("createManager con email ya registrado lanza BusinessException")
    void createManager_emailYaRegistrado_lanzaBusinessException() {
        User admin = User.builder().id(1L).empresa(Company.builder().id(1L).build()).build();
        CreateManagerRequest request = new CreateManagerRequest("Nuevo Gestor", "gestor2@nxtime.test", "password1");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> service.createManager(request, admin)).isInstanceOf(BusinessException.class);
    }

    // ---- changePassword ----

    @Test
    @DisplayName("changePassword con la contraseña antigua correcta la actualiza")
    void changePassword_contrasenaAntiguaCorrecta_laActualiza() {
        User user = User.builder().id(1L).contrasena("hashViejo").build();
        ChangePasswordRequest request = new ChangePasswordRequest("viejo123", "nuevo123");
        when(passwordEncoder.matches(request.contrasenaAntigua(), user.getContrasena())).thenReturn(true);
        when(passwordEncoder.encode(request.contrasenaNueva())).thenReturn("hashNuevo");

        service.changePassword(request, user);

        assertThat(user.getContrasena()).isEqualTo("hashNuevo");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("changePassword con la contraseña antigua incorrecta lanza BusinessException 400")
    void changePassword_contrasenaAntiguaIncorrecta_lanzaBusinessException() {
        User user = User.builder().id(1L).contrasena("hashViejo").build();
        ChangePasswordRequest request = new ChangePasswordRequest("incorrecta", "nuevo123");
        when(passwordEncoder.matches(request.contrasenaAntigua(), user.getContrasena())).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(request, user)).isInstanceOf(BusinessException.class);
        verify(userRepository, never()).save(any());
    }

    // ---- setEmployeeActive ----

    @Test
    @DisplayName("setEmployeeActive(false) da de baja a un empleado de la misma empresa")
    void setEmployeeActive_false_daDeBajaAlEmpleado() {
        Company empresa = Company.builder().id(1L).build();
        User manager = User.builder().id(1L).empresa(empresa).build();
        User employee = User.builder().id(2L).empresa(empresa).activo(true).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

        service.setEmployeeActive(2L, false, manager);

        assertThat(employee.isActivo()).isFalse();
        assertThat(employee.getFechaBaja()).isNotNull();
    }

    @Test
    @DisplayName("setEmployeeActive sobre un empleado de OTRA empresa lanza TenantAccessException")
    void setEmployeeActive_empleadoDeOtraEmpresa_lanzaTenantAccessException() {
        User manager = User.builder().id(1L).empresa(Company.builder().id(1L).build()).build();
        User employee = User.builder().id(2L).empresa(Company.builder().id(2L).build()).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.setEmployeeActive(2L, false, manager))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("setEmployeeActive sobre un empleado inexistente lanza ResourceNotFoundException")
    void setEmployeeActive_empleadoInexistente_lanzaResourceNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        User manager = User.builder().id(1L).empresa(Company.builder().id(1L).build()).build();

        assertThatThrownBy(() -> service.setEmployeeActive(99L, false, manager))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
