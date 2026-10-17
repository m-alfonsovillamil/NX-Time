package com.nxtime.nxtime.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.notification.EmailSender;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

/**
 * Los hallazgos de la revisión de seguridad del 17/10/2026 (plan J, fase J1)
 * que se ven por HTTP, con el servidor arrancado y PostgreSQL real.
 *
 * Cada test reproduce un ataque concreto y <b>falla sin su arreglo</b>: se
 * comprobó quitándolo antes de dar el arreglo por bueno.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
class RevisionDeSeguridadIT {

    private static final String CONTRASENA = "unaContrasena123";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "revision_seguridad_it_" + System.nanoTime();
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

    /** Aquí no sale ningún correo. */
    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper json = new ObjectMapper();

    /** Una IP por petición: el límite por IP no es lo que se prueba aquí. */
    private static final AtomicInteger IP = new AtomicInteger(1);

    private Company empresa;

    @BeforeEach
    void preparar() {
        empresa = companyRepository.save(Company.builder().nombre("Revisión " + System.nanoTime()).build());
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private User persona(String nombre, Role rol) {
        return userRepository.save(User.builder()
                .nombre(nombre).apellidos("Pruebas").email(nombre.toLowerCase() + System.nanoTime() + "@revision.test")
                .contrasena(passwordEncoder.encode(CONTRASENA)).rol(rol).empresa(empresa).activo(true)
                .build());
    }

    private HttpHeaders cabeceras(String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        int n = IP.getAndIncrement();
        cabeceras.set("X-Forwarded-For", "203.0." + (n / 250) + "." + (n % 250 + 1));
        if (token != null) {
            cabeceras.setBearerAuth(token);
        }
        return cabeceras;
    }

    private ResponseEntity<String> post(String ruta, String token, String cuerpo) {
        return rest.exchange(ruta, HttpMethod.POST, new HttpEntity<>(cuerpo, cabeceras(token)), String.class);
    }

    private ResponseEntity<String> login(User quien, String contrasena) {
        return post("/auth/login", null,
                "{\"email\":\"%s\",\"contrasena\":\"%s\"}".formatted(quien.getEmail(), contrasena));
    }

    private String token(User quien) {
        ResponseEntity<String> respuesta = login(quien, CONTRASENA);
        assertThat(respuesta.getStatusCode().value()).as(respuesta.getBody()).isEqualTo(200);
        return json.readTree(respuesta.getBody()).get("token").asText();
    }

    // ------------------------------------------------------------------
    // J1.3 — probar contraseñas con una sesión ajena
    // ------------------------------------------------------------------

    /**
     * Cuántas de {@code veces} pruebas seguidas llegan a comprobar la
     * contraseña. Sin límite son todas. Con él, la espera tras el quinto fallo
     * y el tope de diez por minuto cortan antes; cuál de los dos corta cada
     * una depende de lo que tarde cada petición, así que se mira el total y no
     * la respuesta de una en concreto.
     */
    private long probadas(int veces, java.util.function.IntFunction<ResponseEntity<String>> prueba,
            int codigoSiFalla) {
        java.util.List<Integer> codigos = new java.util.ArrayList<>();
        for (int i = 0; i < veces; i++) {
            codigos.add(prueba.apply(i).getStatusCode().value());
        }
        assertThat(codigos).as("solo fallos o límites").containsOnly(codigoSiFalla, 429);
        return codigos.stream().filter(codigo -> codigo == codigoSiFalla).count();
    }

    @Test
    @DisplayName("Con un access token ajeno no se pueden probar contraseñas sin freno en verificar-contrasena")
    void verificarContrasenaTieneLimite() {
        String suyo = token(persona("Ana", Role.EMPLEADO));

        long probadas = probadas(15, i -> post("/api/v1/perfil/verificar-contrasena", suyo,
                "{\"contrasena\":\"prueba-" + i + "\"}"), 401);

        assertThat(probadas).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("Tampoco cambiando la contraseña: la antigua tiene el mismo límite")
    void cambiarContrasenaTieneLimite() {
        String suyo = token(persona("Ana", Role.EMPLEADO));

        long probadas = probadas(15, i -> post("/api/v1/usuario/cambiar-contrasena", suyo,
                "{\"contrasenaAntigua\":\"prueba-%d\",\"contrasenaNueva\":\"otraContrasena123\"}".formatted(i)), 400);

        assertThat(probadas).isLessThanOrEqualTo(10);
    }

    // ------------------------------------------------------------------
    // J1.4 — dejar a la empresa sin administrador
    // ------------------------------------------------------------------

    private int cambiarEstado(String token, User quien, boolean activo) {
        return rest.exchange("/api/v1/gestor/empleados/" + quien.getId() + "/estado", HttpMethod.PATCH,
                new HttpEntity<>("{\"activo\":" + activo + "}", cabeceras(token)), String.class)
                .getStatusCode().value();
    }

    private boolean activo(User quien) {
        return userRepository.findById(quien.getId()).orElseThrow().isActivo();
    }

    @Test
    @DisplayName("RRHH no puede dar de baja a un ADMIN")
    void rrhhNoDaDeBajaAUnAdmin() {
        User admin = persona("Admin", Role.ADMIN);
        User rrhh = persona("Rrhh", Role.RRHH);

        assertThat(cambiarEstado(token(rrhh), admin, false)).isEqualTo(403);
        assertThat(activo(admin)).isTrue();
    }

    @Test
    @DisplayName("El único ADMIN no puede darse de baja a sí mismo")
    void elUnicoAdminNoSeDaDeBaja() {
        User admin = persona("Admin", Role.ADMIN);

        assertThat(cambiarEstado(token(admin), admin, false)).isEqualTo(403);
        assertThat(activo(admin)).isTrue();
    }

    @Test
    @DisplayName("Lo de siempre sigue funcionando: RRHH da de baja a un empleado y un ADMIN a otro ADMIN")
    void lasBajasNormalesSiguen() {
        User rrhh = persona("Rrhh", Role.RRHH);
        User empleado = persona("Empleado", Role.EMPLEADO);
        User admin = persona("Admin", Role.ADMIN);
        User otroAdmin = persona("Otro", Role.ADMIN);

        assertThat(cambiarEstado(token(rrhh), empleado, false)).isEqualTo(200);
        assertThat(cambiarEstado(token(admin), otroAdmin, false)).isEqualTo(200);
        assertThat(activo(empleado)).isFalse();
        assertThat(activo(otroAdmin)).isFalse();
    }

    // ------------------------------------------------------------------
    // Sesiones de quien se da de baja
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Dar de baja a alguien le cierra las sesiones: su refresh ya no renueva")
    void laBajaCierraLasSesiones() {
        User admin = persona("Admin", Role.ADMIN);
        User empleado = persona("Empleado", Role.EMPLEADO);
        ResponseEntity<String> sesion = login(empleado, CONTRASENA);
        String refresh = json.readTree(sesion.getBody()).get("refreshToken").asText();

        assertThat(cambiarEstado(token(admin), empleado, false)).isEqualTo(200);

        assertThat(post("/auth/refresh", null, "{\"refreshToken\":\"" + refresh + "\"}").getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("Un refresh vivo de una cuenta de baja no renueva aunque nadie lo haya revocado")
    void elRefreshDeUnaCuentaDeBajaNoRenueva() {
        User empleado = persona("Empleado", Role.EMPLEADO);
        String refresh = json.readTree(login(empleado, CONTRASENA).getBody()).get("refreshToken").asText();
        // De baja por debajo, sin pasar por el servicio que revoca.
        empleado.setActivo(false);
        userRepository.save(empleado);

        assertThat(post("/auth/refresh", null, "{\"refreshToken\":\"" + refresh + "\"}").getStatusCode().value())
                .isEqualTo(401);
    }

    @Autowired
    private org.springframework.security.authentication.AuthenticationProvider proveedor;

    /**
     * Si la cuenta de baja se mirara antes de la contraseña, como hace Spring
     * por defecto, la respuesta llegaría sin el BCrypt y el tiempo delataría
     * qué correos tienen cuenta. No se cronometra (sería un test que falla a
     * ratos): se comprueba el orden, que es la causa.
     */
    @Test
    @DisplayName("En una cuenta de baja se comprueba la contraseña antes de decir que está de baja")
    void laBajaSeMiraDespuesDeLaContrasena() {
        User deBaja = persona("Baja", Role.EMPLEADO);
        deBaja.setActivo(false);
        userRepository.save(deBaja);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> proveedor.authenticate(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                deBaja.getEmail(), "no-es-la-suya")))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> proveedor.authenticate(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                deBaja.getEmail(), CONTRASENA)))
                .isInstanceOf(org.springframework.security.authentication.DisabledException.class);
    }

    @Test
    @DisplayName("Con la contraseña buena, verificar y cambiar siguen funcionando")
    void conLaBuenaFunciona() {
        User ana = persona("Ana", Role.EMPLEADO);
        String suyo = token(ana);

        assertThat(post("/api/v1/perfil/verificar-contrasena", suyo, "{\"contrasena\":\"" + CONTRASENA + "\"}")
                .getStatusCode().value()).isEqualTo(204);
        assertThat(post("/api/v1/usuario/cambiar-contrasena", suyo,
                "{\"contrasenaAntigua\":\"%s\",\"contrasenaNueva\":\"otraContrasena123\"}".formatted(CONTRASENA))
                .getStatusCode().value()).isEqualTo(200);
        assertThat(login(ana, "otraContrasena123").getStatusCode().value()).isEqualTo(200);
    }
}
