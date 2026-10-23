package com.nxtime.nxtime.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.CompanyStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.notification.EmailSender;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.web.support.CodigosEnviados;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El alta de empresas con la aprobación del operador (plan J, ADR 046), de punta
 * a punta: registrar, esperar, aprobar o rechazar, suspender y reactivar, y lo
 * que queda escrito. Con la aprobación ENCENDIDA, como en producción (en el
 * resto de tests va apagada: ver src/test/resources/application.properties).
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
@Import(CodigosEnviados.class)
class AltaConAprobacionIT {

    private static final String CONTRASENA = "unaContrasena123";
    private static final String OPERADOR = "operador@alta.test";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "alta_aprobacion_it_" + System.nanoTime();
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
        registry.add("application.alta.requiere-aprobacion", () -> "true");
        registry.add("application.plataforma.operadores", () -> OPERADOR);
    }

    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private CodigosEnviados codigos;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private com.nxtime.nxtime.service.AuthService authService;

    private final ObjectMapper json = new ObjectMapper();
    private static final AtomicInteger IP = new AtomicInteger(1);

    private String tokenOperador;

    @BeforeEach
    void operador() {
        if (userRepository.findByEmail(OPERADOR).isEmpty()) {
            Company suya = companyRepository.save(Company.builder().nombre("La del operador").build());
            userRepository.save(User.builder().nombre("Olga").apellidos("Operadora").email(OPERADOR)
                    .contrasena(passwordEncoder.encode(CONTRASENA)).rol(Role.ADMIN).empresa(suya).activo(true).build());
        }
        // El operador tiene que entrar con segundo factor (ADR 051): por la
        // puerta del SSO, que cuenta como tal y abre la misma sesión que el login.
        tokenOperador = authService.abrirSesion(userRepository.findByEmail(OPERADOR).orElseThrow().getId(),
                com.nxtime.nxtime.domain.RefreshToken.Origen.ANDROID).token();
    }

    // ------------------------------------------------------------------

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

    private ResponseEntity<String> get(String ruta, String token) {
        return rest.exchange(ruta, HttpMethod.GET, new HttpEntity<>(cabeceras(token)), String.class);
    }

    private ResponseEntity<String> entrar(String correo) {
        return post("/auth/login", null, "{\"email\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, CONTRASENA));
    }

    private String token(ResponseEntity<String> sesion) {
        assertThat(sesion.getStatusCode().value()).as(sesion.getBody()).isEqualTo(200);
        return json.readTree(sesion.getBody()).get("token").asText();
    }

    private String detalle(ResponseEntity<String> respuesta) {
        return json.readTree(respuesta.getBody()).path("detail").asText();
    }

    /** Registra una empresa y confirma el correo. Devuelve la respuesta de la confirmación. */
    private ResponseEntity<String> registrarYConfirmar(String empresa, String correo) {
        assertThat(post("/auth/register-manager", null, """
                {"aceptaCondiciones":true,"nombreEmpresa":"%s","nombre":"Ana","apellidos":"Alta","email":"%s",
                 "contrasena":"%s","origen":"WEB"}""".formatted(empresa, correo, CONTRASENA)).getStatusCode().value())
                .isEqualTo(202);
        return post("/auth/registro/confirmar", null,
                "{\"email\":\"%s\",\"codigo\":\"%s\"}".formatted(correo, codigos.ultimoPara(correo)));
    }

    private Company empresa(String nombre) {
        return companyRepository.findByNombre(nombre).orElseThrow();
    }

    private int accion(String verbo, long empresaId, String token) {
        return post("/api/v1/plataforma/empresas/" + empresaId + "/" + verbo, token, "{\"motivo\":\"Prueba\"}")
                .getStatusCode().value();
    }

    private static String nombre(String base) {
        return base + " " + System.nanoTime();
    }

    private static String correo() {
        return "ana" + System.nanoTime() + "@alta.test";
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("Registrar deja la empresa pendiente: al confirmar el correo, 423 para quien la registró y un correo al operador")
    void registrarLaDejaPendiente() {
        String nombre = nombre("Talleres");
        String correo = correo();

        ResponseEntity<String> confirmada = registrarYConfirmar(nombre, correo);

        assertThat(confirmada.getStatusCode().value()).isEqualTo(423);
        assertThat(detalle(confirmada)).contains("pendiente de aprobación");
        Company empresa = empresa(nombre);
        assertThat(empresa.getEstado()).isEqualTo(CompanyStatus.PENDIENTE);
        assertThat(empresa.getCondicionesVersion()).isEqualTo("2026-10-18");
        assertThat(empresa.getCondicionesAceptadasEn()).isNotNull();
        assertThat(empresa.getCondicionesAceptadasPor()).isEqualTo(userRepository.findByEmail(correo).orElseThrow().getId());
        // El código se ha canjeado aunque la respuesta sea un error: el correo está confirmado.
        assertThat(userRepository.findByEmail(correo).orElseThrow().correoPendienteDeConfirmar()).isFalse();
        verify(emailSender, timeout(5_000)).enviar(eq(OPERADOR), anyString(), eq("company-awaiting-approval"), anyMap());

        ResponseEntity<String> entrada = entrar(correo);
        assertThat(entrada.getStatusCode().value()).isEqualTo(423);
        assertThat(detalle(entrada)).contains("pendiente de aprobación");
    }

    @Test
    @DisplayName("Sin marcar la casilla de las condiciones no se registra nada")
    void sinAceptarNoSeRegistra() {
        String nombre = nombre("Sin casilla");
        ResponseEntity<String> registro = post("/auth/register-manager", null, """
                {"nombreEmpresa":"%s","nombre":"Ana","apellidos":"Alta","email":"%s","contrasena":"%s"}"""
                .formatted(nombre, correo(), CONTRASENA));

        assertThat(registro.getStatusCode().value()).isEqualTo(400);
        assertThat(companyRepository.findByNombre(nombre)).isEmpty();
    }

    @Test
    @DisplayName("Aprobar: la empresa pasa a activa, su ADMIN entra y recibe el aviso, y queda en el historial")
    void aprobar() {
        String nombre = nombre("Aprobada");
        String correo = correo();
        registrarYConfirmar(nombre, correo);
        long id = empresa(nombre).getId();

        assertThat(accion("aprobar", id, tokenOperador)).isEqualTo(204);

        assertThat(empresa(nombre).getEstado()).isEqualTo(CompanyStatus.ACTIVA);
        assertThat(empresa(nombre).getAprobadaEn()).isNotNull();
        token(entrar(correo));
        verify(emailSender, timeout(5_000)).enviar(eq(correo), anyString(), eq("company-approved"), anyMap());
        JsonNode historial = json.readTree(get("/api/v1/plataforma/empresas/" + id, tokenOperador).getBody()).get("historial");
        // Lo más reciente primero: la consulta de ahora, y antes la aprobación.
        assertThat(historial.get(0).get("accion").asText()).isEqualTo("CONSULTAR");
        assertThat(historial.get(1).get("accion").asText()).isEqualTo("APROBAR");
        assertThat(historial.get(1).get("operador").asText()).isEqualTo(OPERADOR);
        // Aprobar dos veces no tiene sentido.
        assertThat(accion("aprobar", id, tokenOperador)).isEqualTo(409);
    }

    @Test
    @DisplayName("Rechazar: se avisa a quien la registró y se borra entera, pero su historial queda")
    void rechazar() {
        String nombre = nombre("Rechazada");
        String correo = correo();
        registrarYConfirmar(nombre, correo);
        long id = empresa(nombre).getId();

        assertThat(accion("rechazar", id, tokenOperador)).isEqualTo(204);

        assertThat(companyRepository.findByNombre(nombre)).isEmpty();
        assertThat(userRepository.findByEmail(correo)).isEmpty();
        verify(emailSender, timeout(5_000)).enviar(eq(correo), anyString(), eq("company-rejected"), anyMap());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM acciones_de_plataforma WHERE empresa_id = ? AND accion = 'RECHAZAR' AND empresa_nombre = ?",
                Integer.class, id, nombre)).isEqualTo(1);
    }

    @Test
    @DisplayName("Suspender: fuera todas sus sesiones y su token deja de valer al momento; reactivar le devuelve el acceso")
    void suspenderYReactivar() {
        String nombre = nombre("Suspendida");
        String correo = correo();
        registrarYConfirmar(nombre, correo);
        long id = empresa(nombre).getId();
        accion("aprobar", id, tokenOperador);
        ResponseEntity<String> sesion = entrar(correo);
        String suToken = token(sesion);
        String refresh = json.readTree(sesion.getBody()).get("refreshToken").asText();
        assertThat(get("/api/v1/perfil", suToken).getStatusCode().value()).isEqualTo(200);

        assertThat(accion("suspender", id, tokenOperador)).isEqualTo(204);

        assertThat(get("/api/v1/perfil", suToken).getStatusCode().value()).as("el access token ya no vale").isEqualTo(401);
        assertThat(post("/auth/refresh", null, "{\"refreshToken\":\"" + refresh + "\"}").getStatusCode().value())
                .isEqualTo(401);
        ResponseEntity<String> entrada = entrar(correo);
        assertThat(entrada.getStatusCode().value()).isEqualTo(423);
        assertThat(detalle(entrada)).contains("suspendido");
        verify(emailSender, timeout(5_000)).enviar(eq(correo), anyString(), eq("company-suspended"), anyMap());

        assertThat(accion("reactivar", id, tokenOperador)).isEqualTo(204);
        token(entrar(correo));
    }

    @Test
    @DisplayName("Solo el operador actúa, y no puede suspender su propia empresa")
    void soloElOperador() {
        String nombre = nombre("Ajena");
        String correo = correo();
        registrarYConfirmar(nombre, correo);
        long id = empresa(nombre).getId();
        accion("aprobar", id, tokenOperador);
        String suToken = token(entrar(correo));

        // Un ADMIN de una empresa no es el operador del servicio.
        assertThat(accion("suspender", userRepository.findByEmail(OPERADOR).orElseThrow().getEmpresa().getId(), suToken))
                .isEqualTo(403);
        assertThat(accion("suspender", userRepository.findByEmail(OPERADOR).orElseThrow().getEmpresa().getId(),
                tokenOperador)).isEqualTo(409);
    }

    @Test
    @DisplayName("El historial del operador no se puede cambiar ni borrar desde la aplicación")
    void elHistorialEsDeSoloInsercion() {
        String nombre = nombre("Consultada");
        registrarYConfirmar(nombre, correo());
        long id = empresa(nombre).getId();
        get("/api/v1/plataforma/empresas/" + id, tokenOperador);

        // Lo para el REVOKE (permiso denegado) y, si algún día el rol tuviera
        // el permiso, el trigger: cualquiera de los dos vale.
        String loPara = "(?s).*(permiso denegado|permission denied|solo inserción).*";
        assertThatThrownBy(() -> jdbc.update("UPDATE acciones_de_plataforma SET motivo = 'retocado' WHERE empresa_id = ?", id))
                .rootCause().hasMessageMatching(loPara);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM acciones_de_plataforma WHERE empresa_id = ?", id))
                .rootCause().hasMessageMatching(loPara);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acciones_de_plataforma WHERE empresa_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Condiciones nuevas: al ADMIN de una empresa sin la versión en vigor se le piden al entrar, y al aceptarlas ya no")
    void condicionesNuevas() {
        String nombre = nombre("Antigua");
        String correo = correo();
        registrarYConfirmar(nombre, correo);
        long id = empresa(nombre).getId();
        accion("aprobar", id, tokenOperador);
        // Como una empresa de antes de la V43.
        jdbc.update("UPDATE empresas SET condiciones_version = NULL WHERE id = ?", id);

        ResponseEntity<String> sesion = entrar(correo);
        assertThat(json.readTree(sesion.getBody()).get("condicionesPorAceptar").asBoolean()).isTrue();
        String suToken = token(sesion);
        assertThat(json.readTree(get("/api/v1/empresa/condiciones", suToken).getBody()).get("porAceptar").asBoolean())
                .isTrue();

        ResponseEntity<String> aceptadas = post("/api/v1/empresa/condiciones/aceptar", suToken, "");
        assertThat(aceptadas.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(aceptadas.getBody()).get("versionAceptada").asText()).isEqualTo("2026-10-18");
        assertThat(json.readTree(aceptadas.getBody()).get("aceptadaPor").asText()).isEqualTo("Ana Alta");

        assertThat(json.readTree(entrar(correo).getBody()).get("condicionesPorAceptar").asBoolean()).isFalse();
    }
}
