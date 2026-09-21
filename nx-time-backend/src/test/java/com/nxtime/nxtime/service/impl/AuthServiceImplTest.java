package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;

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
import com.nxtime.nxtime.security.LimitadorDeIntentosPorCuenta;
import com.nxtime.nxtime.service.AccessCodeService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

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
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AccessCodeService accessCodeService;

    /*
     * Real y no simulado: es un contador en memoria sin dependencias, y
     * simularlo dejaría sin probar que el login pasa por él. Cada test
     * arranca con uno nuevo, así que los diez intentos por minuto no se
     * gastan entre tests.
     */
    private LimitadorDeIntentosPorCuenta limitadorPorCuenta;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        limitadorPorCuenta = new LimitadorDeIntentosPorCuenta();
        service = new AuthServiceImpl(
                userRepository, companyRepository, refreshTokenRepository, passwordEncoder, jwtService,
                authenticationManager, eventPublisher, accessCodeService, limitadorPorCuenta);
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
                new RegisterManagerRequest("Empresa Nueva SL", "Ada", "Lovelace",
                        "ada@nxtime.test", "password123");
        when(companyRepository.findByNombre(request.nombreEmpresa())).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class)))
                .thenReturn(Company.builder().id(1L).nombre(request.nombreEmpresa()).build());
        when(passwordEncoder.encode(request.contrasena())).thenReturn("hash");
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
                new RegisterManagerRequest("Empresa Repetida SL", "Ada", "Lovelace",
                        "ada@nxtime.test", "password123");
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

    /*
     * El login tiene que pasar por el limitador POR CUENTA, no solo por el
     * de IP: ese se esquiva cambiando de sitio, y durante un tiempo se pudo
     * esquivar además con una cabecera inventada.
     */
    @Test
    @DisplayName("Al pasarse de intentos contra la misma cuenta, login corta con 429 sin llegar a autenticar")
    void login_demasiadosIntentosContraLaMismaCuenta_corta() {
        Company empresa = Company.builder().id(1L).nombre("Empresa").build();
        User user = User.builder().id(1L).email("gestor@nxtime.test").rol(Role.GESTOR).empresa(empresa).build();
        LoginRequest request = new LoginRequest(user.getEmail(), "password123");
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        for (int i = 0; i < 10; i++) {
            service.login(request);
        }

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Demasiados intentos");
        // Y se corta ANTES de comprobar la contraseña: diez llamadas, no once.
        verify(authenticationManager, times(10)).authenticate(any());
    }

    // ---- refreshAccessToken (rotacion, Fase A11) ----

    /** Como lo guarda el servicio: por el hash, nunca por el token. */
    private RefreshToken guardado(String token, User user, java.util.UUID familia) {
        return RefreshToken.builder()
                .id(1L)
                .tokenHash(AuthServiceImpl.hashDe(token))
                .usuario(user)
                .familia(familia)
                .origen(RefreshToken.Origen.ANDROID)
                .expiraEn(Instant.now().plusSeconds(3600))
                .revocado(false)
                .build();
    }

    @Test
    @DisplayName("Renovar emite un refresh NUEVO y deja el anterior rotado y apuntando a su sucesor")
    void refreshAccessToken_tokenVivo_rotaElRefresh() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").rol(Role.EMPLEADO).build();
        java.util.UUID familia = java.util.UUID.randomUUID();
        RefreshToken stored = guardado("refresh-abc", user, familia);
        when(refreshTokenRepository.findByTokenHash(AuthServiceImpl.hashDe("refresh-abc")))
                .thenReturn(Optional.of(stored));
        // El sucesor sale del save(), no de releerlo: dentro de la misma
        // transacción el INSERT todavía no se ha volcado y una consulta por su
        // hash no lo encontraría.
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthenticationResponse response = service.refreshAccessToken("refresh-abc");

        assertThat(response.token()).isEqualTo("access-token");
        // Lo que cambia respecto a antes: ya no devuelve el mismo.
        assertThat(response.refreshToken()).isNotEqualTo("refresh-abc").isNotBlank();
        assertThat(stored.estaRotado()).isTrue();
        assertThat(stored.getSustituidoPor()).isNotNull();
    }

    @Test
    @DisplayName("El sucesor hereda la familia y el origen: sigue siendo la misma sesion")
    void refreshAccessToken_elSucesorHeredaFamiliaYOrigen() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").rol(Role.EMPLEADO).build();
        java.util.UUID familia = java.util.UUID.randomUUID();
        RefreshToken stored = guardado("refresh-abc", user, familia);
        stored.setOrigen(RefreshToken.Origen.WEB);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        service.refreshAccessToken("refresh-abc");

        ArgumentCaptor<RefreshToken> emitido = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, atLeastOnce()).save(emitido.capture());
        RefreshToken sucesor = emitido.getAllValues().get(0);
        assertThat(sucesor.getFamilia()).isEqualTo(familia);
        assertThat(sucesor.getOrigen()).isEqualTo(RefreshToken.Origen.WEB);
    }

    /*
     * El caso que da sentido a toda la fase.
     *
     * Dos clientes con el mismo token rotado significa que alguien tiene una
     * copia. No hay forma de saber cual es el legitimo, asi que caen los dos.
     */
    @Test
    @DisplayName("Reutilizar un token YA ROTADO revoca la familia entera")
    void refreshAccessToken_tokenReutilizado_revocaLaFamilia() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").build();
        java.util.UUID familia = java.util.UUID.randomUUID();
        RefreshToken rotado = guardado("refresh-viejo", user, familia);
        rotado.setRotadoEn(Instant.now().minusSeconds(60));
        rotado.setSustituidoPor(RefreshToken.builder().id(2L).build());
        when(refreshTokenRepository.findByTokenHash(AuthServiceImpl.hashDe("refresh-viejo")))
                .thenReturn(Optional.of(rotado));

        assertThatThrownBy(() -> service.refreshAccessToken("refresh-viejo"))
                .isInstanceOf(BadCredentialsException.class);

        verify(refreshTokenRepository).revocarLaFamilia(eq(familia), any());
    }

    @Test
    @DisplayName("Un token que no existe lanza BadCredentialsException, y no revoca nada")
    void refreshAccessToken_tokenInexistente_lanzaBadCredentialsException() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refreshAccessToken("no-existe"))
                .isInstanceOf(BadCredentialsException.class);
        // Un token inventado no puede cerrarle la sesion a nadie.
        verify(refreshTokenRepository, never()).revocarLaFamilia(any(), any());
    }

    @Test
    @DisplayName("Un token revocado lanza BadCredentialsException")
    void refreshAccessToken_tokenRevocado_lanzaBadCredentialsException() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").build();
        RefreshToken stored = guardado("refresh-abc", user, java.util.UUID.randomUUID());
        stored.revocar(Instant.now());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refreshAccessToken("refresh-abc"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Un token caducado lanza BadCredentialsException")
    void refreshAccessToken_tokenCaducado_lanzaBadCredentialsException() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").build();
        RefreshToken stored = guardado("refresh-abc", user, java.util.UUID.randomUUID());
        stored.setExpiraEn(Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.refreshAccessToken("refresh-abc"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("El token no se guarda en claro: en la base solo queda su sha256")
    void login_guardaSoloElHashDelRefresh() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").rol(Role.EMPLEADO).build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        AuthenticationResponse respuesta = service.login(new LoginRequest(user.getEmail(), "password"));

        ArgumentCaptor<RefreshToken> emitido = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(emitido.capture());
        assertThat(emitido.getValue().getTokenHash())
                .isEqualTo(AuthServiceImpl.hashDe(respuesta.refreshToken()))
                .isNotEqualTo(respuesta.refreshToken());
    }

    @Test
    @DisplayName("Un login desde la web da un refresh de 12 horas, no de 30 dias")
    void login_desdeWeb_duraMenos() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").rol(Role.EMPLEADO).build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        service.login(new LoginRequest(user.getEmail(), "password", "WEB"));

        ArgumentCaptor<RefreshToken> emitido = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(emitido.capture());
        assertThat(emitido.getValue().getOrigen()).isEqualTo(RefreshToken.Origen.WEB);
        // Un navegador es una maquina que carga codigo de terceros y que a
        // menudo se comparte.
        assertThat(emitido.getValue().getExpiraEn())
                .isBefore(Instant.now().plus(java.time.Duration.ofHours(13)));
    }

    // ---- logout ----

    @Test
    @DisplayName("logout revoca la FAMILIA entera, no solo el token que trae el cliente")
    void logout_tokenExistente_revocaLaFamilia() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").build();
        java.util.UUID familia = java.util.UUID.randomUUID();
        RefreshToken stored = guardado("refresh-abc", user, familia);
        when(refreshTokenRepository.findByTokenHash(AuthServiceImpl.hashDe("refresh-abc")))
                .thenReturn(Optional.of(stored));

        service.logout("refresh-abc");

        // Si cayera solo este, un token anterior de la cadena podria reabrirla.
        verify(refreshTokenRepository).revocarLaFamilia(eq(familia), any());
    }

    @Test
    @DisplayName("logout de un token que no existe es idempotente (no lanza, no revela nada)")
    void logout_tokenInexistente_esIdempotente() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        service.logout("no-existe");

        verify(refreshTokenRepository, never()).revocarLaFamilia(any(), any());
    }

    // ---- createEmployee / createManager ----

    @Test
    @DisplayName("createEmployee con email ya registrado lanza BusinessException, sin crear nada ni mandar código")
    void createEmployee_emailYaRegistrado_lanzaBusinessException() {
        User manager = User.builder().id(1L).empresa(Company.builder().id(1L).build()).build();
        CreateEmployeeRequest request = new CreateEmployeeRequest("Nuevo", "Empleado", "nuevo@nxtime.test");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> service.createEmployee(request, manager)).isInstanceOf(BusinessException.class);
        verify(userRepository, never()).save(any());
        verify(accessCodeService, never()).emitirCodigoDeAlta(any(), any());
    }

    @Test
    @DisplayName("createEmployee crea al empleado sin una contraseña que nadie conozca y le manda el código de alta")
    void createEmployee_emailLibre_creaEmpleadoYEmiteCodigoDeAlta() {
        Company empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        User manager = User.builder().id(1L).empresa(empresa).build();
        CreateEmployeeRequest request = new CreateEmployeeRequest("Nuevo", "Empleado", "nuevo@nxtime.test");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        // Lo que se cifra es azar: ninguna contraseña viaja en la petición.
        when(passwordEncoder.encode(any())).thenReturn("hash-de-azar");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createEmployee(request, manager);

        verify(userRepository).save(argThat(u -> u.getRol() == Role.EMPLEADO && u.getEmpresa() == empresa
                && "hash-de-azar".equals(u.getContrasena())));
        verify(accessCodeService).emitirCodigoDeAlta(
                argThat(u -> "nuevo@nxtime.test".equals(u.getEmail())), argThat("Empresa Test"::equals));
        verify(eventPublisher).publishEvent(any(com.nxtime.nxtime.notification.NotificationEvents.EmployeeCreated.class));
    }

    @Test
    @DisplayName("createEmployee: si el código de alta no sale, el error sube (para deshacer el alta) y no se anuncia la bienvenida")
    void createEmployee_siElCodigoNoSale_lanzaYNoPublicaLaBienvenida() {
        User manager = User.builder().id(1L).empresa(Company.builder().id(1L).nombre("Empresa Test").build()).build();
        CreateEmployeeRequest request = new CreateEmployeeRequest("Nuevo", "Empleado", "nuevo@nxtime.test");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        org.mockito.Mockito.doThrow(new BusinessException("Sin correo",
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
                .when(accessCodeService).emitirCodigoDeAlta(any(), any());

        assertThatThrownBy(() -> service.createEmployee(request, manager)).isInstanceOf(BusinessException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("createManager con email ya registrado lanza BusinessException")
    void createManager_emailYaRegistrado_lanzaBusinessException() {
        User admin = User.builder().id(1L).empresa(Company.builder().id(1L).build()).build();
        CreateManagerRequest request = new CreateManagerRequest("Nuevo", "Gestor", "gestor2@nxtime.test");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> service.createManager(request, admin)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("createManager crea al gestor y le manda el código de alta, igual que a un empleado")
    void createManager_emailLibre_creaGestorYEmiteCodigoDeAlta() {
        Company empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        User admin = User.builder().id(1L).empresa(empresa).build();
        CreateManagerRequest request = new CreateManagerRequest("Nuevo", "Gestor", "gestor2@nxtime.test");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createManager(request, admin);

        verify(userRepository).save(argThat(u -> u.getRol() == Role.GESTOR && u.getEmpresa() == empresa));
        verify(accessCodeService).emitirCodigoDeAlta(
                argThat(u -> "gestor2@nxtime.test".equals(u.getEmail())), argThat("Empresa Test"::equals));
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

    // ---- cerrarTodasLasSesiones ----

    @Test
    @DisplayName("cerrarTodasLasSesiones revoca los refresh tokens y no toca la contraseña")
    void cerrarTodasLasSesiones_revocaLosRefreshTokens() {
        User user = User.builder().id(1L).email("ana@nxtime.test").contrasena("hash").build();
        when(refreshTokenRepository.revocarTodasLasDe(eq(user), any())).thenReturn(3);

        service.cerrarTodasLasSesiones(user);

        // Lo que se corta es la capacidad de RENOVAR: la contraseña sigue
        // valiendo, porque esto no es un robo de credenciales sino "quiero
        // echar a quien haya quedado dentro en otro móvil".
        verify(refreshTokenRepository).revocarTodasLasDe(eq(user), any());
        assertThat(user.getContrasena()).isEqualTo("hash");
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
