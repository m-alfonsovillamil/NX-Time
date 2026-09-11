package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.nxtime.nxtime.domain.AccessCode;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.CreateEmployeeRequest;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.notification.EmailNotSentException;
import com.nxtime.nxtime.notification.EmailSender;
import com.nxtime.nxtime.repository.AccessCodeRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.RefreshTokenRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AccessCodeService;
import com.nxtime.nxtime.service.AuthService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Test de INTEGRACIÓN de los códigos de acceso (ADR 014), contra
 * PostgreSQL real y con las transacciones de verdad.
 *
 * Existe por tres cosas que un test con mocks no puede ver:
 *
 *  1. Que un código incorrecto SUMA el intento aunque la petición falle.
 *     Depende de {@code noRollbackFor} en una transacción real: si alguien
 *     lo quita, el unitario sigue en verde y los intentos pasan a ser
 *     infinitos.
 *  2. Que un alta cuyo correo no sale NO deja la cuenta creada: el rollback
 *     de la transacción del alta.
 *  3. Que fijar la contraseña cierra de verdad las sesiones abiertas (un
 *     UPDATE en JPQL sobre refresh_tokens, con el rol nxtime_app).
 *
 * El correo es un mock: el código se lee de lo que se le pasó.
 *
 * Requisito: `docker compose up -d postgres` (ver ApiContractTest).
 */
@SpringBootTest
class AccessCodeIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "access_code_it_" + System.nanoTime();
        String adminUrl = "jdbc:postgresql://localhost:5433/nxtime";
        try (Connection admin = DriverManager.getConnection(adminUrl, "nxtime", "nxtime");
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + testDb);
        }

        String testUrl = "jdbc:postgresql://localhost:5433/" + testDb;
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> "nxtime_app");
        registry.add("spring.datasource.password", () -> "nxtime_app");
        registry.add("spring.flyway.url", () -> testUrl);
        registry.add("spring.flyway.user", () -> "nxtime");
        registry.add("spring.flyway.password", () -> "nxtime");
    }

    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private AccessCodeService accessCodeService;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private AccessCodeRepository accessCodeRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User crearUsuario(String email, Role rol, String contrasena) {
        Company empresa = companyRepository.save(Company.builder().nombre("Empresa " + email).build());
        return userRepository.save(User.builder()
                .email(email).nombre("Persona").contrasena(passwordEncoder.encode(contrasena))
                .rol(rol).empresa(empresa).build());
    }

    private String ultimoCodigoEnviadoA(String email) {
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.captor();
        verify(emailSender, atLeastOnce()).enviarObligatorio(eq(email), anyString(), anyString(), variables.capture());
        return (String) variables.getValue().get("codigo");
    }

    private List<AccessCode> codigosDe(User usuario) {
        return accessCodeRepository.findAll().stream()
                .filter(codigo -> codigo.getUsuario().getId() == usuario.getId())
                .toList();
    }

    @Test
    @DisplayName("Los intentos fallidos se quedan guardados aunque cada petición falle, y al quinto el código muere")
    void losIntentosFallidos_sobrevivenAlError() {
        User usuario = crearUsuario("intentos@nxtime.test", Role.EMPLEADO, "antigua12345");
        accessCodeService.solicitarRecuperacion(usuario.getEmail());
        String correcto = ultimoCodigoEnviadoA(usuario.getEmail());
        String incorrecto = correcto.equals("000000") ? "111111" : "000000";

        for (int intento = 1; intento <= AccessCode.MAXIMO_INTENTOS; intento++) {
            assertThatThrownBy(() -> accessCodeService.confirmar(usuario.getEmail(), incorrecto, "nuevaSegura123"))
                    .isInstanceOf(BusinessException.class);
        }

        AccessCode codigo = codigosDe(usuario).get(0);
        assertThat(codigo.getIntentosFallidos()).isEqualTo(AccessCode.MAXIMO_INTENTOS);
        assertThat(codigo.getAnuladoEn()).isNotNull();

        assertThatThrownBy(() -> accessCodeService.confirmar(usuario.getEmail(), correcto, "nuevaSegura123"))
                .isInstanceOf(BusinessException.class);
        User sinCambios = userRepository.findByEmail(usuario.getEmail()).orElseThrow();
        assertThat(passwordEncoder.matches("antigua12345", sinCambios.getContrasena())).isTrue();
    }

    @Test
    @DisplayName("El código correcto fija la contraseña y cierra las sesiones que había abiertas")
    void elCodigoCorrecto_fijaLaContrasena_yCierraLasSesiones() {
        User usuario = crearUsuario("recupera@nxtime.test", Role.EMPLEADO, "antigua12345");
        AuthenticationResponse sesion = authService.login(new LoginRequest(usuario.getEmail(), "antigua12345"));

        accessCodeService.solicitarRecuperacion(usuario.getEmail());
        accessCodeService.confirmar(usuario.getEmail(), ultimoCodigoEnviadoA(usuario.getEmail()), "nuevaSegura123");

        User actualizado = userRepository.findByEmail(usuario.getEmail()).orElseThrow();
        assertThat(passwordEncoder.matches("nuevaSegura123", actualizado.getContrasena())).isTrue();
        assertThat(codigosDe(usuario).get(0).getUsadoEn()).isNotNull();
        assertThat(refreshTokenRepository.findByToken(sesion.refreshToken()).orElseThrow().isRevocado()).isTrue();
        assertThatThrownBy(() -> authService.refreshAccessToken(sesion.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(authService.login(new LoginRequest(usuario.getEmail(), "nuevaSegura123")).token()).isNotBlank();
    }

    @Test
    @DisplayName("Un alta cuyo correo no sale no deja la cuenta creada: ni usuario ni código")
    void unAltaSinCorreo_noDejaLaCuentaCreada() {
        User gestor = crearUsuario("gestor.alta@nxtime.test", Role.GESTOR, "gestor12345");
        doThrow(new EmailNotSentException("SMTP caído", new RuntimeException("Connection refused")))
                .when(emailSender).enviarObligatorio(eq("sin.correo@nxtime.test"), anyString(), anyString(), anyMap());

        assertThatThrownBy(() -> authService.createEmployee(
                new CreateEmployeeRequest("Sin Correo", "sin.correo@nxtime.test"), gestor))
                .isInstanceOf(BusinessException.class);

        assertThat(userRepository.existsByEmail("sin.correo@nxtime.test")).isFalse();
    }

    @Test
    @DisplayName("Un alta con correo crea la cuenta sin contraseña utilizable, y el código de alta la activa")
    void unAltaConCorreo_seActivaConElCodigo() {
        User gestor = crearUsuario("gestor.activa@nxtime.test", Role.GESTOR, "gestor12345");

        authService.createEmployee(new CreateEmployeeRequest("Nueva", "nueva@nxtime.test"), gestor);

        assertThatThrownBy(() -> authService.login(new LoginRequest("nueva@nxtime.test", "cualquiera123")))
                .isInstanceOf(BadCredentialsException.class);
        accessCodeService.confirmar("nueva@nxtime.test", ultimoCodigoEnviadoA("nueva@nxtime.test"), "miPropia12345");
        assertThat(authService.login(new LoginRequest("nueva@nxtime.test", "miPropia12345")).token()).isNotBlank();
    }
}
