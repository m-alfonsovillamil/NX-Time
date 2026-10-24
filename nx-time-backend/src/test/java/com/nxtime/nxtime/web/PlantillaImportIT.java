package com.nxtime.nxtime.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.notification.EmailSender;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.DepartmentRepository;
import com.nxtime.nxtime.repository.InvitacionPendienteRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.repository.VacationBalanceRepository;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Importar una plantilla de punta a punta (plan K; ADR 052): comprobar, crear
 * todo o nada, los permisos de cada columna, las invitaciones en tandas de 50
 * y el cambio de rol. Cada test en su propia empresa: el cupo de 50 altas al
 * día es por empresa.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
class PlantillaImportIT {

    private static final String CONTRASENA = "una frase bastante larga";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "plantilla_import_it_" + System.nanoTime();
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

    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private VacationBalanceRepository vacationBalanceRepository;
    @Autowired
    private InvitacionPendienteRepository invitacionPendienteRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper json = new ObjectMapper();
    private static final AtomicInteger N = new AtomicInteger(1);
    private static final AtomicInteger IP = new AtomicInteger(1);

    // ------------------------------------------------------------------

    private Company empresa() {
        return companyRepository.save(Company.builder().nombre("Importadora " + N.getAndIncrement()).build());
    }

    private User persona(Company empresa, Role rol) {
        return userRepository.save(User.builder().nombre("Ana").apellidos("López")
                .email("p" + N.getAndIncrement() + "@importadora.test")
                .contrasena(passwordEncoder.encode(CONTRASENA)).rol(rol).empresa(empresa).activo(true).build());
    }

    private String token(User persona) {
        ResponseEntity<String> login = rest.exchange("/auth/login", HttpMethod.POST, new HttpEntity<>(
                "{\"email\":\"%s\",\"contrasena\":\"%s\"}".formatted(persona.getEmail(), CONTRASENA), cabeceras(null,
                        MediaType.APPLICATION_JSON)), String.class);
        assertThat(login.getStatusCode().value()).as(login.getBody()).isEqualTo(200);
        return json.readTree(login.getBody()).get("token").asText();
    }

    private HttpHeaders cabeceras(String token, MediaType tipo) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(tipo);
        int n = IP.getAndIncrement();
        cabeceras.set("X-Forwarded-For", "192.0." + (n / 250) + "." + (n % 250 + 1));
        if (token != null) {
            cabeceras.setBearerAuth(token);
        }
        return cabeceras;
    }

    private ResponseEntity<String> subir(String token, String csv, boolean simular) {
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("fichero", new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "plantilla.csv";
            }
        });
        return rest.exchange("/api/v1/gestor/plantilla/importar?simular=" + simular, HttpMethod.POST,
                new HttpEntity<>(partes, cabeceras(token, MediaType.MULTIPART_FORM_DATA)), String.class);
    }

    private JsonNode cuerpo(ResponseEntity<String> respuesta) {
        return json.readTree(respuesta.getBody());
    }

    private List<String> errores(JsonNode respuesta, int fila) {
        List<String> errores = new ArrayList<>();
        for (JsonNode f : respuesta.get("detalle")) {
            if (f.get("fila").asInt() == fila) {
                f.get("errores").forEach(e -> errores.add(e.asText()));
            }
        }
        return errores;
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("Simular comprueba cada fila sin crear a nadie: correos repetidos o con cuenta, departamento, jornada, rol")
    void simularDiceLosErroresDeCadaFila() {
        Company empresa = empresa();
        User admin = persona(empresa, Role.ADMIN);
        User yaExiste = persona(empresa(), Role.EMPLEADO);
        departmentRepository.save(Department.builder().empresa(empresa).nombre("Almacén").build());
        String csv = """
                nombre;apellidos;email;rol;departamento;horas;vacaciones
                Luis;Martín;luis@importadora.test;;almacen;37,5;23
                Eva;Ruiz;LUIS@importadora.test;gestora;;;
                Pau;Gil;%s;;;;
                Sara;Ríos;sara@importadora.test;jefa;Ventas;99;2,5
                ;;no-es-un-correo;;;;
                """.formatted(yaExiste.getEmail());

        ResponseEntity<String> respuesta = subir(token(admin), csv, true);

        assertThat(respuesta.getStatusCode().value()).as(respuesta.getBody()).isEqualTo(200);
        JsonNode r = cuerpo(respuesta);
        assertThat(r.get("filas").asInt()).isEqualTo(5);
        assertThat(r.get("conErrores").asInt()).isEqualTo(4);
        assertThat(r.get("creadas").asInt()).isZero();
        // La primera vale: el departamento casa sin tildes ni mayúsculas.
        assertThat(errores(r, 2)).isEmpty();
        assertThat(r.get("detalle").get(0).get("departamento").asText()).isEqualTo("Almacén");
        assertThat(errores(r, 3)).anyMatch(e -> e.contains("repetido") && e.contains("fila 2"));
        assertThat(errores(r, 4)).anyMatch(e -> e.contains("ya tiene cuenta"));
        assertThat(errores(r, 5)).anyMatch(e -> e.contains("Rol desconocido"))
                .anyMatch(e -> e.contains("Ventas"))
                .anyMatch(e -> e.contains("jornada"))
                .anyMatch(e -> e.contains("vacaciones"));
        assertThat(errores(r, 6)).anyMatch(e -> e.contains("nombre")).anyMatch(e -> e.contains("correo"));
        assertThat(userRepository.findByEmail("luis@importadora.test")).isEmpty();
    }

    @Test
    @DisplayName("Con una fila mala no se crea nadie: o todas o ninguna")
    void todasONinguna() {
        Company empresa = empresa();
        User admin = persona(empresa, Role.ADMIN);
        ResponseEntity<String> respuesta = subir(token(admin), """
                nombre;apellidos;email
                Luis;Martín;luis.ninguno@importadora.test
                ;Ruiz;eva.ninguna@importadora.test
                """, false);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(cuerpo(respuesta).get("detail").asText()).contains("1 fila con errores");
        assertThat(userRepository.findByEmail("luis.ninguno@importadora.test")).isEmpty();
    }

    @Test
    @DisplayName("Importar crea a todos con su rol, departamento, jornada y vacaciones, y les manda la invitación")
    void importaYInvita() {
        Company empresa = empresa();
        User admin = persona(empresa, Role.ADMIN);
        departmentRepository.save(Department.builder().empresa(empresa).nombre("Almacén").build());

        ResponseEntity<String> respuesta = subir(token(admin), """
                Nombre,Apellidos,Email,Rol,Departamento,Horas semanales,Vacaciones
                Luis,Martín,luis.si@importadora.test,,Almacén,"37,5",23
                Marta,Sanz,marta.si@importadora.test,gestora,,,
                Elena,Ríos,elena.si@importadora.test,RRHH,,,
                """, false);

        assertThat(respuesta.getStatusCode().value()).as(respuesta.getBody()).isEqualTo(200);
        JsonNode r = cuerpo(respuesta);
        assertThat(r.get("creadas").asInt()).isEqualTo(3);
        assertThat(r.get("invitaciones").get("enCamino").asInt()).isEqualTo(3);

        User luis = userRepository.findByEmail("luis.si@importadora.test").orElseThrow();
        assertThat(luis.getRol()).isEqualTo(Role.EMPLEADO);
        assertThat(luis.getDepartamento().getNombre()).isEqualTo("Almacén");
        assertThat(luis.getHorasSemanales()).isEqualByComparingTo("37.5");
        int anio = LocalDate.now(ZoneId.of("Europe/Madrid")).getYear();
        assertThat(vacationBalanceRepository.findByUsuarioAndAnio(luis, anio).orElseThrow().getDiasTotales()).isEqualTo(23);
        assertThat(userRepository.findByEmail("marta.si@importadora.test").orElseThrow().getRol()).isEqualTo(Role.GESTOR);
        assertThat(userRepository.findByEmail("elena.si@importadora.test").orElseThrow().getRol()).isEqualTo(Role.RRHH);

        // Las invitaciones salen en segundo plano, con el correo de alta de siempre.
        verify(emailSender, timeout(10_000).times(1))
                .enviarObligatorio(eq("luis.si@importadora.test"), anyString(), eq("access-code-welcome"), anyMap());
        esperar(Duration.ofSeconds(10), () -> invitacionPendienteRepository.countByEmpresaId(empresa.getId()) == 0);
    }

    @Test
    @DisplayName("Un GESTOR importa empleados, pero no da roles ni departamentos ni toca jornadas")
    void permisosDeCadaColumna() {
        Company empresa = empresa();
        User gestor = persona(empresa, Role.GESTOR);
        departmentRepository.save(Department.builder().empresa(empresa).nombre("Almacén").build());

        JsonNode r = cuerpo(subir(token(gestor), """
                nombre;apellidos;email;rol;departamento;horas;vacaciones
                Luis;Martín;luis.gestor@importadora.test;;;;
                Eva;Ruiz;eva.gestor@importadora.test;rrhh;Almacén;40;22
                """, true));

        assertThat(errores(r, 2)).isEmpty();
        assertThat(errores(r, 3))
                .anyMatch(e -> e.contains("rol RRHH"))
                .anyMatch(e -> e.contains("departamentos"))
                .anyMatch(e -> e.contains("jornada"))
                .anyMatch(e -> e.contains("vacaciones"));
    }

    @Test
    @DisplayName("Más de 50 en un día: las cuentas se crean todas, y las invitaciones que no caben esperan")
    void invitacionesEnTandas() {
        Company empresa = empresa();
        User admin = persona(empresa, Role.ADMIN);
        StringBuilder csv = new StringBuilder("nombre;apellidos;email\n");
        for (int i = 0; i < 55; i++) {
            csv.append("Persona;Número ").append(i).append(";tanda").append(i).append("@importadora.test\n");
        }
        String token = token(admin);

        JsonNode r = cuerpo(subir(token, csv.toString(), false));

        assertThat(r.get("creadas").asInt()).isEqualTo(55);
        assertThat(r.get("invitaciones").get("enCamino").asInt()).isEqualTo(50);
        esperar(Duration.ofSeconds(30), () -> invitacionPendienteRepository.countByEmpresaId(empresa.getId()) == 5);
        verify(emailSender, timeout(10_000).times(50))
                .enviarObligatorio(anyString(), anyString(), eq("access-code-welcome"), anyMap());

        // Hoy ya no caben más: pedirlo no manda nada.
        ResponseEntity<String> otraVez = rest.exchange("/api/v1/gestor/plantilla/invitaciones/enviar", HttpMethod.POST,
                new HttpEntity<>(cabeceras(token, MediaType.APPLICATION_JSON)), String.class);
        JsonNode estado = cuerpo(otraVez);
        assertThat(estado.get("pendientes").asInt()).isEqualTo(5);
        assertThat(estado.get("cupoHoy").asInt()).isZero();
        assertThat(estado.get("enCamino").asInt()).isZero();

        // Y en la plantilla se ve quién espera.
        JsonNode plantilla = cuerpo(rest.exchange("/api/v1/gestor/plantilla", HttpMethod.GET,
                new HttpEntity<>(cabeceras(token, MediaType.APPLICATION_JSON)), String.class));
        int esperando = 0;
        for (JsonNode p : plantilla) {
            if (p.get("invitacionPendiente").asBoolean()) {
                esperando++;
            }
        }
        assertThat(esperando).isEqualTo(5);
    }

    @Test
    @DisplayName("Un ADMIN cambia el rol de alguien, y vale ya; no el suyo, y RRHH no puede")
    void cambiarRol() {
        Company empresa = empresa();
        User admin = persona(empresa, Role.ADMIN);
        User ana = persona(empresa, Role.EMPLEADO);
        User rrhh = persona(empresa, Role.RRHH);
        String tokenAdmin = token(admin);
        String tokenAna = token(ana);
        assertThat(rest.exchange("/api/v1/gestor/plantilla", HttpMethod.GET,
                new HttpEntity<>(cabeceras(tokenAna, MediaType.APPLICATION_JSON)), String.class).getStatusCode().value())
                .isEqualTo(403);

        assertThat(cambiar(tokenAdmin, ana.getId(), "RRHH")).isEqualTo(204);
        // Con el mismo token: los permisos se leen en cada petición.
        assertThat(rest.exchange("/api/v1/gestor/plantilla", HttpMethod.GET,
                new HttpEntity<>(cabeceras(tokenAna, MediaType.APPLICATION_JSON)), String.class).getStatusCode().value())
                .isEqualTo(200);

        assertThat(cambiar(tokenAdmin, admin.getId(), "EMPLEADO")).isEqualTo(403);
        assertThat(cambiar(token(rrhh), ana.getId(), "ADMIN")).isEqualTo(403);
        User otra = persona(empresa(), Role.EMPLEADO);
        assertThat(cambiar(tokenAdmin, otra.getId(), "GESTOR")).isEqualTo(403);
        assertThat(userRepository.findById(otra.getId()).orElseThrow().getRol()).isEqualTo(Role.EMPLEADO);
    }

    /** Hasta que se cumpla, o falla: lo que va en segundo plano tarda lo que tarda. */
    private static void esperar(Duration tope, java.util.function.BooleanSupplier condicion) {
        long limite = System.nanoTime() + tope.toNanos();
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("No se ha cumplido en " + tope.toSeconds() + " s");
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private int cambiar(String token, long id, String rol) {
        return rest.exchange("/api/v1/gestor/empleados/" + id + "/rol", HttpMethod.PATCH,
                new HttpEntity<>("{\"rol\":\"" + rol + "\"}", cabeceras(token, MediaType.APPLICATION_JSON)),
                String.class).getStatusCode().value();
    }
}
