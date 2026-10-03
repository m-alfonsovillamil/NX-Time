package com.nxtime.nxtime.web;

import com.nxtime.nxtime.web.support.CodigosEnviados;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.KioskProfileService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El kiosco de fichaje de punta a punta, con la cadena de seguridad de verdad
 * (ADR 033): emparejar una tablet, fichar con tarjeta y con PIN, los límites,
 * lo que un token de kiosco NO abre, revocar, y que la cadena de auditoría
 * sigue intacta con los fichajes hechos en él.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(CodigosEnviados.class)
class KioscoIT {

    private static final String CONTRASENA = "unaContrasena123";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "kiosco_it_" + System.nanoTime();
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

    @Autowired private TestRestTemplate rest;
    @Autowired private com.nxtime.nxtime.repository.KioskRepository kioskRepository;
    @Autowired private com.nxtime.nxtime.repository.NoticeRepository noticeRepository;
    @Autowired private CodigosEnviados codigos;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private TimeEntryRepository timeEntryRepository;
    @Autowired private TimeEntryAuditRepository auditRepository;
    @Autowired private KioskProfileService perfilDeKiosco;

    private final ObjectMapper json = new ObjectMapper();

    /** Una IP por test: emparejar y el alta van limitados por IP (LoginRateLimitFilter). */
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private String ip;

    private String tokenAdmin;
    private User admin;
    private User lucia;

    @BeforeEach
    void empresaConAdminYEmpleada() throws Exception {
        ip = "203.0.113." + SIGUIENTE_IP.getAndIncrement();
        String email = "admin." + System.nanoTime() + "@nxtime.test";
        ResponseEntity<String> alta = post("/auth/register-manager", """
                {"nombreEmpresa":"Almacenes %s","nombre":"Raúl","apellidos":"Admin","email":"%s","contrasena":"%s"}
                """.formatted(System.nanoTime(), email, CONTRASENA), null);
        assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        // Desde la V37, el correo se confirma antes de entrar.
        ResponseEntity<String> confirmado = post("/auth/registro/confirmar",
                "{\"email\":\"%s\",\"codigo\":\"%s\"}".formatted(email, codigos.ultimoPara(email)), null);
        assertThat(confirmado.getStatusCode()).isEqualTo(HttpStatus.OK);
        tokenAdmin = json.readTree(confirmado.getBody()).get("token").asText();
        admin = userRepository.findByEmail(email).orElseThrow();
        lucia = empleada(admin.getEmpresa(), "Lucía");
    }

    @Test
    @DisplayName("Emparejar: pendiente, el ADMIN teclea el código, el token se entrega una sola vez")
    void emparejar() throws Exception {
        JsonNode pedido = cuerpo(post("/kiosco/emparejar", "{}", null));
        String codigo = pedido.get("codigo").asText();
        String secreto = pedido.get("secreto").asText();
        assertThat(codigo).hasSize(8);

        assertThat(estado(secreto).get("estado").asText()).isEqualTo("PENDIENTE");

        // Tecleado con minúsculas y un guion: se admite igual.
        String tecleado = codigo.substring(0, 4).toLowerCase() + "-" + codigo.substring(4);
        ResponseEntity<String> confirmado = post("/api/v1/empresa/kioscos",
                "{\"codigo\":\"%s\",\"nombre\":\"Entrada almacén\"}".formatted(tecleado), bearer(tokenAdmin));
        assertThat(confirmado.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode listo = estado(secreto);
        assertThat(listo.get("estado").asText()).isEqualTo("LISTO");
        assertThat(listo.get("token").asText()).isNotBlank();
        assertThat(listo.get("kiosco").get("nombre").asText()).isEqualTo("Entrada almacén");

        JsonNode otraVez = estado(secreto);
        assertThat(otraVez.get("estado").asText()).isEqualTo("ENTREGADO");
        assertThat(otraVez.get("token").isNull()).isTrue();

        // El mismo código no sirve dos veces.
        assertThat(post("/api/v1/empresa/kioscos",
                "{\"codigo\":\"%s\",\"nombre\":\"Otra\"}".formatted(codigo), bearer(tokenAdmin)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Probar códigos de kiosco tiene tope: al undécimo intento en una hora, 429")
    void probarCodigosTieneTope() {
        // Diez: KioskServiceImpl.CONFIRMACIONES_POR_HORA.
        for (int i = 0; i < 10; i++) {
            assertThat(post("/api/v1/empresa/kioscos", "{\"codigo\":\"ZZZZZZZZ\",\"nombre\":\"Prueba\"}",
                    bearer(tokenAdmin)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(post("/api/v1/empresa/kioscos", "{\"codigo\":\"ZZZZZZZZ\",\"nombre\":\"Prueba\"}",
                bearer(tokenAdmin)).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Un token de kiosco solo abre /kiosco; un JWT de persona no abre el kiosco")
    void loQueAbreCadaToken() {
        String kiosco = kioscoEmparejado();

        assertThat(get("/kiosco/yo", kioscoAuth(kiosco)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/kiosco/yo", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/kiosco/yo", kioscoAuth("inventado")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Con el token del kiosco no se llega a nada de la API de las personas.
        assertThat(get("/api/v1/perfil", kioscoAuth(kiosco)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Y un ADMIN con su sesión no es un kiosco.
        assertThat(get("/kiosco/yo", bearer(tokenAdmin)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Con la tarjeta: identificar, fichar la entrada, y en la auditoría queda el kiosco")
    void ficharConTarjeta() throws Exception {
        String kiosco = kioscoEmparejado();
        String qr = perfilDeKiosco.tarjeta(lucia).codigo();

        JsonNode quien = cuerpo(post("/kiosco/identificar", "{\"qr\":\"%s\"}".formatted(qr), kioscoAuth(kiosco)));
        assertThat(quien.get("nombre").asText()).isEqualTo("Lucía");
        assertThat(quien.get("estado").asText()).isEqualTo("SIN_JORNADA");

        ResponseEntity<String> entrada = post("/kiosco/fichar",
                "{\"qr\":\"%s\",\"tipo\":\"INICIO\"}".formatted(qr), kioscoAuth(kiosco));
        assertThat(entrada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cuerpo(entrada).get("instante").asText()).isNotBlank();

        TimeEntry abierta = timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(lucia).orElseThrow();
        assertThat(abierta.getKiosco()).isNotNull();
        // El kiosco es LAZY desde el ADR 034: aquí, sin sesión, se mira por su
        // id (que el proxy sabe sin consultar) y no por su nombre.
        assertThat(kioskRepository.findById(abierta.getKiosco().getId()).orElseThrow().getNombre())
                .isEqualTo("Entrada almacén");
        List<TimeEntryAudit> traza = auditRepository.findByRegistro_IdOrderByFechaHoraAsc(abierta.getId());
        assertThat(traza).singleElement().satisfies(fila -> {
            assertThat(fila.getUsuario().getId()).isEqualTo(lucia.getId());
            assertThat(fila.getMotivo()).contains("Desde el kiosco «Entrada almacén»");
        });

        // Ya trabajando: identificarse otra vez lo dice, y fichar la entrada otra vez no se puede.
        assertThat(cuerpo(post("/kiosco/identificar", "{\"qr\":\"%s\"}".formatted(qr), kioscoAuth(kiosco)))
                .get("estado").asText()).isEqualTo("TRABAJANDO");
        assertThat(post("/kiosco/fichar", "{\"qr\":\"%s\",\"tipo\":\"INICIO\"}".formatted(qr), kioscoAuth(kiosco))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/kiosco/fichar", "{\"qr\":\"%s\",\"tipo\":\"FIN\"}".formatted(qr), kioscoAuth(kiosco))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // Y la cadena de huellas sigue intacta con estas filas dentro.
        JsonNode integridad = cuerpo(get("/api/v1/auditoria/integridad", bearer(tokenAdmin)));
        assertThat(integridad.get("intacta").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Una tarjeta regenerada, o de otra empresa, no vale")
    void tarjetasQueNoValen() {
        String kiosco = kioscoEmparejado();
        String vieja = perfilDeKiosco.tarjeta(lucia).codigo();
        String nueva = perfilDeKiosco.regenerarTarjeta(lucia).codigo();

        assertThat(post("/kiosco/identificar", "{\"qr\":\"%s\"}".formatted(vieja), kioscoAuth(kiosco))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/kiosco/identificar", "{\"qr\":\"%s\"}".formatted(nueva), kioscoAuth(kiosco))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        User deFuera = empleada(companyRepository.save(Company.builder().nombre("Otra " + System.nanoTime()).build()),
                "Ajena");
        String ajena = perfilDeKiosco.tarjeta(deFuera).codigo();
        assertThat(post("/kiosco/identificar", "{\"qr\":\"%s\"}".formatted(ajena), kioscoAuth(kiosco))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Una firma tocada a mano tampoco: se cambia su último carácter por otro.
        char ultimo = nueva.charAt(nueva.length() - 1);
        String tocada = nueva.substring(0, nueva.length() - 1) + (ultimo == '0' ? '1' : '0');
        assertThat(post("/kiosco/identificar", "{\"qr\":\"%s\"}".formatted(tocada), kioscoAuth(kiosco))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Con el PIN: sale en la lista, cinco fallos lo bloquean aunque después se acierte")
    void ficharConPin() throws Exception {
        String kiosco = kioscoEmparejado();
        assertThat(cuerpo(get("/kiosco/plantilla", kioscoAuth(kiosco)))).isEmpty();

        perfilDeKiosco.fijarPin(lucia, "4827");
        JsonNode lista = cuerpo(get("/kiosco/plantilla", kioscoAuth(kiosco)));
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("nombre").asText()).isEqualTo("Lucía");
        assertThat(lista.get(0).has("email")).isFalse();

        String bueno = "{\"usuarioId\":%d,\"pin\":\"4827\"}".formatted(lucia.getId());
        String malo = "{\"usuarioId\":%d,\"pin\":\"0000\"}".formatted(lucia.getId());
        assertThat(post("/kiosco/identificar", bueno, kioscoAuth(kiosco)).getStatusCode()).isEqualTo(HttpStatus.OK);

        for (int i = 0; i < 5; i++) {
            assertThat(post("/kiosco/identificar", malo, kioscoAuth(kiosco)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(post("/kiosco/identificar", bueno, kioscoAuth(kiosco)).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // Cambiar el PIN desde el perfil lo desbloquea: quien lo cambia ya sabe cuál es.
        perfilDeKiosco.fijarPin(lucia, "5938");
        assertThat(post("/kiosco/identificar", "{\"usuarioId\":%d,\"pin\":\"5938\"}".formatted(lucia.getId()),
                kioscoAuth(kiosco)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Tres bloqueos y el PIN se anula, aunque el dueño acierte entre medias; y se avisa a ella y al ADMIN")
    void tresBloqueosAnulanElPin() throws Exception {
        String kiosco = kioscoEmparejado();
        perfilDeKiosco.fijarPin(lucia, "4827");
        String bueno = "{\"usuarioId\":%d,\"pin\":\"4827\"}".formatted(lucia.getId());
        String malo = "{\"usuarioId\":%d,\"pin\":\"0000\"}".formatted(lucia.getId());

        for (int bloqueo = 1; bloqueo <= 3; bloqueo++) {
            for (int i = 0; i < 5; i++) {
                assertThat(post("/kiosco/identificar", malo, kioscoAuth(kiosco)).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN);
            }
            if (bloqueo < 3) {
                // Pasan los quince minutos (a mano: no se espera en un test) y el
                // dueño acierta. Antes, eso devolvía el contador a cero.
                User conBloqueo = userRepository.findById(lucia.getId()).orElseThrow();
                conBloqueo.setKioscoPinBloqueadoHasta(null);
                userRepository.save(conBloqueo);
                assertThat(post("/kiosco/identificar", bueno, kioscoAuth(kiosco)).getStatusCode())
                        .isEqualTo(HttpStatus.OK);
            }
        }

        User anulada = userRepository.findById(lucia.getId()).orElseThrow();
        assertThat(anulada.getKioscoPinHash()).isNull();
        // Ni con el PIN bueno: ya no hay PIN.
        assertThat(post("/kiosco/identificar", bueno, kioscoAuth(kiosco)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        // Los avisos salen después del commit y en otro hilo.
        long limite = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < limite
                && (avisos(lucia, NoticeType.PIN_KIOSCO_ANULADO) == 0
                        || avisos(admin, NoticeType.PIN_KIOSCO_ANULADO_EQUIPO) == 0)) {
            Thread.sleep(100);
        }
        assertThat(avisos(lucia, NoticeType.PIN_KIOSCO_ANULADO))
                .isEqualTo(1);
        assertThat(avisos(admin, NoticeType.PIN_KIOSCO_ANULADO_EQUIPO))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Las tarjetas de toda la plantilla son un POST del ADMIN; con un GET o sin empresa:configurar, no")
    void tarjetasDeLaPlantilla() throws Exception {
        assertThat(post("/api/v1/empresa/kioscos/tarjetas", "{}", bearer(tokenAdmin)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/empresa/kioscos/tarjetas", bearer(tokenAdmin)).getStatusCode().is2xxSuccessful())
                .isFalse();
        assertThat(get("/api/v1/gestor/kiosco/tarjetas", bearer(tokenAdmin)).getStatusCode().is2xxSuccessful())
                .isFalse();
    }

    @Test
    @DisplayName("Un kiosco revocado deja de valer en la siguiente petición")
    void revocar() throws Exception {
        String kiosco = kioscoEmparejado();
        JsonNode lista = cuerpo(get("/api/v1/empresa/kioscos", bearer(tokenAdmin)));
        long id = lista.get(0).get("id").asLong();

        ResponseEntity<String> revocado = rest.exchange("/api/v1/empresa/kioscos/" + id, HttpMethod.DELETE,
                new HttpEntity<>(bearer(tokenAdmin)), String.class);
        assertThat(revocado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/kiosco/yo", kioscoAuth(kiosco)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(cuerpo(get("/api/v1/empresa/kioscos", bearer(tokenAdmin))).get(0).get("activo").asBoolean())
                .isFalse();
    }

    // ------------------------------------------------------------------

    /** Cuántos avisos de ese tipo tiene esa persona. */
    private long avisos(User persona, NoticeType tipo) {
        return noticeRepository.findByDestinatarioOrderByCreadoEnDesc(persona).stream()
                .filter(aviso -> aviso.getTipo() == tipo)
                .count();
    }

    /** Empareja una tablet con la empresa del ADMIN y devuelve su token. */
    private String kioscoEmparejado() {
        try {
            JsonNode pedido = cuerpo(post("/kiosco/emparejar", "{}", null));
            post("/api/v1/empresa/kioscos", "{\"codigo\":\"%s\",\"nombre\":\"Entrada almacén\"}"
                    .formatted(pedido.get("codigo").asText()), bearer(tokenAdmin));
            return estado(pedido.get("secreto").asText()).get("token").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private User empleada(Company empresa, String nombre) {
        return userRepository.save(User.builder()
                .nombre(nombre).email(nombre.toLowerCase() + System.nanoTime() + "@nxtime.test").contrasena("x")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true)
                .build());
    }

    private JsonNode estado(String secreto) throws Exception {
        return cuerpo(post("/kiosco/emparejar/estado", "{\"secreto\":\"%s\"}".formatted(secreto), null));
    }

    private ResponseEntity<String> post(String ruta, String cuerpo, HttpHeaders auth) {
        HttpHeaders cabeceras = auth == null ? new HttpHeaders() : auth;
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        cabeceras.set("X-Forwarded-For", ip);
        return rest.exchange(ruta, HttpMethod.POST, new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    private ResponseEntity<String> get(String ruta, HttpHeaders auth) {
        return rest.exchange(ruta, HttpMethod.GET, new HttpEntity<>(auth == null ? new HttpHeaders() : auth),
                String.class);
    }

    private JsonNode cuerpo(ResponseEntity<String> respuesta) throws Exception {
        assertThat(respuesta.getStatusCode().is2xxSuccessful())
                .as("respuesta %s: %s", respuesta.getStatusCode(), respuesta.getBody())
                .isTrue();
        return json.readTree(respuesta.getBody());
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(token);
        return cabeceras;
    }

    private static HttpHeaders kioscoAuth(String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set(HttpHeaders.AUTHORIZATION, "Kiosco " + token);
        return cabeceras;
    }
}
