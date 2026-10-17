package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.ConfirmRegistrationRequest;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.notification.EmailSender;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AuthService;
import com.nxtime.nxtime.web.support.CodigosEnviados;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Registrar una empresa encima del registro a medias de otro (revisión de
 * seguridad del 17/10/2026, plan J), con los servicios de verdad y PostgreSQL.
 *
 * El ataque: alguien empieza a registrar su empresa y, antes de que canjee el
 * código, otro registra con el mismo correo y una contraseña suya. Al dueño le
 * llega otro código a su buzón; lo canjea, y hasta entonces la cuenta quedaba
 * con la contraseña del otro. Lo mismo al revés: el otro se adelanta y el dueño
 * se registra después.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
@Import(CodigosEnviados.class)
class RegistroConCorreoAjenoIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "registro_ajeno_it_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection("jdbc:postgresql://localhost:5433/nxtime", "nxtime", "nxtime");
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

    /** Aquí no sale ningún correo: los códigos se recogen del evento (CodigosEnviados). */
    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CodigosEnviados codigos;

    private static final String LA_DEL_DUENO = "la-del-dueno-123";
    private static final String LA_DEL_OTRO = "la-del-otro-456";

    private static String correo() {
        return "dueno" + System.nanoTime() + "@test.example";
    }

    private void registrar(String correo, String contrasena) {
        authService.registerManager(new RegisterManagerRequest(
                "Empresa " + System.nanoTime(), "Alba", "Pruebas", correo, contrasena, "WEB"));
    }

    /** El dueño del correo canjea el último código que le ha llegado: es el único que tiene. */
    private AuthenticationResponse canjearElUltimoCodigo(String correo) {
        return authService.confirmarRegistro(new ConfirmRegistrationRequest(correo, codigos.ultimoPara(correo), "WEB"));
    }

    private void noEntraCon(String correo, String contrasena) {
        assertThatThrownBy(() -> authService.login(new LoginRequest(correo, contrasena)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Otro registra encima del registro a medias del dueño: el dueño entra con el código y la contraseña del otro no abre nada")
    void elOtroLlegaDespues() {
        String correo = correo();
        registrar(correo, LA_DEL_DUENO);
        registrar(correo, LA_DEL_OTRO);

        assertThat(canjearElUltimoCodigo(correo).token()).isNotBlank();

        noEntraCon(correo, LA_DEL_OTRO);
        // Tampoco la del dueño: no hay forma de saber cuál de las dos era la
        // suya, así que no vale ninguna. Elige otra con un código al correo.
        noEntraCon(correo, LA_DEL_DUENO);
    }

    @Test
    @DisplayName("El otro se adelanta con el correo del dueño y el dueño se registra después: tampoco se queda la cuenta")
    void elOtroLlegaAntes() {
        String correo = correo();
        registrar(correo, LA_DEL_OTRO);
        registrar(correo, LA_DEL_DUENO);

        assertThat(canjearElUltimoCodigo(correo).token()).isNotBlank();

        noEntraCon(correo, LA_DEL_OTRO);
    }

    @Test
    @DisplayName("Repetir el registro con la misma contraseña, porque el código no llegó, la conserva")
    void elDuenoRepiteConLaMisma() {
        String correo = correo();
        registrar(correo, LA_DEL_DUENO);
        registrar(correo, LA_DEL_DUENO);

        canjearElUltimoCodigo(correo);

        assertThat(authService.login(new LoginRequest(correo, LA_DEL_DUENO)).token()).isNotBlank();
    }

    @Test
    @DisplayName("Mientras está a medias, ni la contraseña del dueño ni la del otro dan sesión")
    void sinCanjearNoEntraNadie() {
        String correo = correo();
        registrar(correo, LA_DEL_DUENO);
        registrar(correo, LA_DEL_OTRO);

        noEntraCon(correo, LA_DEL_OTRO);
        noEntraCon(correo, LA_DEL_DUENO);
        assertThat(userRepository.findByEmail(correo).orElseThrow().correoPendienteDeConfirmar()).isTrue();
        assertThatThrownBy(() -> authService.confirmarRegistro(new ConfirmRegistrationRequest(correo, "000000", "WEB")))
                .isInstanceOf(BusinessException.class);
    }
}
