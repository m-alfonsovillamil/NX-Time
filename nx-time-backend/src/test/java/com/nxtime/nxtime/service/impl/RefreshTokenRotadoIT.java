package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.RefreshToken;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.RefreshTokenRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AuthService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La rotación del refresh token, con transacciones de verdad (Fase A11).
 *
 * <h2>Por qué hace falta este test y no bastan los unitarios</h2>
 *
 * {@code AuthServiceImplTest} comprueba que al detectar una reutilización se
 * llama a {@code revocarLaFamilia}. Con mocks eso es todo lo que se puede
 * comprobar — y resultó no ser suficiente.
 *
 * La detección lanza {@code BadCredentialsException} justo después de revocar,
 * y sin {@code noRollbackFor} esa excepción <b>deshace el UPDATE</b>: la
 * familia queda revocada mientras dura la transacción y vuelve a estar viva al
 * salir. El mock veía la llamada y daba el test por bueno; la base decía otra
 * cosa.
 *
 * Se descubrió verificando contra producción: rotar, reutilizar el token viejo
 * y volver a usar el nuevo devolvía 200 donde tenía que devolver 401. Este test
 * es lo que impide que vuelva a pasar.
 */
@SpringBootTest
class RefreshTokenRotadoIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "refresh_rotado_it_" + System.nanoTime();
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

    @Autowired
    private AuthService authService;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String CONTRASENA = "unaContrasenaLarga123";

    private String email;

    @BeforeEach
    void setUp() {
        Company empresa = companyRepository.save(
                Company.builder().nombre("Empresa " + System.nanoTime()).build());
        email = "persona" + System.nanoTime() + "@nxtime.test";
        userRepository.save(User.builder()
                .nombre("Persona").apellidos("Apellidos").email(email)
                .contrasena(passwordEncoder.encode(CONTRASENA))
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build());
    }

    private AuthenticationResponse entrar() {
        return authService.login(new LoginRequest(email, CONTRASENA));
    }

    private AuthenticationResponse entrarDesde(String origen) {
        return authService.login(new LoginRequest(email, CONTRASENA, origen));
    }

    @Test
    @DisplayName("Renovar devuelve un refresh distinto, y el anterior deja de valer")
    void rotaYElViejoMuere() {
        String primero = entrar().refreshToken();

        String segundo = authService.refreshAccessToken(primero).refreshToken();

        assertThat(segundo).isNotEqualTo(primero);
        assertThatThrownBy(() -> authService.refreshAccessToken(primero))
                .isInstanceOf(BadCredentialsException.class);
    }

    /**
     * El test que faltaba.
     *
     * Sin {@code noRollbackFor}, la excepción de la detección deshace la
     * revocación y este assert falla: el token nuevo sigue funcionando.
     */
    @Test
    @DisplayName("Reutilizar un token rotado deja MUERTA la cadena entera, no solo el reutilizado")
    void reutilizarElViejoMataTambienAlNuevo() {
        String primero = entrar().refreshToken();
        String segundo = authService.refreshAccessToken(primero).refreshToken();

        // Alguien tenía una copia del primero y la usa.
        assertThatThrownBy(() -> authService.refreshAccessToken(primero))
                .isInstanceOf(BadCredentialsException.class);

        // Y el legítimo también se queda fuera: no hay forma de saber cuál de
        // los dos era, así que se cierran los dos y se vuelve a entrar.
        assertThatThrownBy(() -> authService.refreshAccessToken(segundo))
                .as("la revocación de la familia tiene que sobrevivir a la excepción")
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Cerrar una sesión no toca las demás de la misma persona")
    void cadaLoginEsSuPropiaFamilia() {
        String movil = entrar().refreshToken();
        String navegador = entrarDesde("WEB").refreshToken();

        // Se compromete la del navegador...
        String navegadorRotado = authService.refreshAccessToken(navegador).refreshToken();
        assertThatThrownBy(() -> authService.refreshAccessToken(navegador))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> authService.refreshAccessToken(navegadorRotado))
                .isInstanceOf(BadCredentialsException.class);

        // ...y la del móvil sigue entera. Por eso cada login abre su familia.
        assertThat(authService.refreshAccessToken(movil).refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("El token no se guarda en claro: en la base solo está su sha256")
    void enLaBaseSoloElHash() {
        String token = entrar().refreshToken();

        assertThat(refreshTokenRepository.findByTokenHash(token))
                .as("buscar por el token en claro no puede encontrar nada")
                .isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(AuthServiceImpl.hashDe(token))).isPresent();
    }

    @Test
    @DisplayName("Un login desde la web dura 12 horas; desde el movil, 30 dias")
    void laVidaDependeDelOrigen() {
        String web = entrarDesde("WEB").refreshToken();
        String android = entrarDesde("ANDROID").refreshToken();

        RefreshToken deWeb = refreshTokenRepository.findByTokenHash(AuthServiceImpl.hashDe(web)).orElseThrow();
        RefreshToken deAndroid =
                refreshTokenRepository.findByTokenHash(AuthServiceImpl.hashDe(android)).orElseThrow();

        assertThat(deWeb.getOrigen()).isEqualTo(RefreshToken.Origen.WEB);
        assertThat(deWeb.getExpiraEn()).isBefore(Instant.now().plus(Duration.ofHours(13)));
        assertThat(deAndroid.getExpiraEn()).isAfter(Instant.now().plus(Duration.ofDays(29)));
    }

    @Test
    @DisplayName("Cerrar sesión mata la familia, no solo el token que trae el cliente")
    void logoutMataLaFamilia() {
        String primero = entrar().refreshToken();
        String segundo = authService.refreshAccessToken(primero).refreshToken();

        authService.logout(segundo);

        assertThatThrownBy(() -> authService.refreshAccessToken(segundo))
                .isInstanceOf(BadCredentialsException.class);
    }
}
