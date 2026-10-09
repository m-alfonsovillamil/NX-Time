package com.nxtime.nxtime.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.DepartmentRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El panel de plataforma de punta a punta (ADR 040): la cadena de seguridad de
 * verdad, PostgreSQL real y tres empresas, dos con actividad y una que se
 * quedó a medio registrar.
 *
 * Dos cosas se prueban aquí y no se pueden probar con mocks:
 *
 * <ul>
 *   <li><b>A quién NO se le abre.</b> Es el único sitio de la aplicación que
 *       enseña algo de todas las empresas. Un ADMIN de cualquiera de ellas
 *       tiene que recibir 403 en todas las rutas y no ver el permiso en su
 *       sesión.</li>
 *   <li><b>Que las cifras de cada empresa son las suyas.</b> Son consultas
 *       nativas que agregan por empresa: una que cruzase mal dos tablas daría
 *       números creíbles de otra.</li>
 * </ul>
 *
 * La operadora es, a propósito, una EMPLEADA rasa: el permiso no tiene nada
 * que ver con el rol.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlataformaIT {

    private static final String CONTRASENA = "unaContrasena123";
    private static final String OPERADORA = "operadora@plataforma.test";
    private static final List<String> RUTAS = List.of(
            "/api/v1/plataforma/resumen",
            "/api/v1/plataforma/empresas",
            "/api/v1/plataforma/empresas/1",
            "/api/v1/plataforma/integridad");

    private static String testUrl;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "plataforma_it_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection("jdbc:postgresql://localhost:5433/nxtime", "nxtime", "nxtime");
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + testDb);
        }
        testUrl = "jdbc:postgresql://localhost:5433/" + testDb;
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> "nxtime_app");
        registry.add("spring.datasource.password", () -> "nxtime_app");
        registry.add("spring.flyway.url", () -> testUrl);
        registry.add("spring.flyway.user", () -> "nxtime");
        registry.add("spring.flyway.password", () -> "nxtime");

        // Con mayúsculas y espacios, como lo escribiría alguien en el panel de Render.
        registry.add("application.plataforma.operadores", () -> " Operadora@Plataforma.Test , nadie@plataforma.test");
        // El tope de lo caro alcanza a estas rutas y se prueba en su test; aquí estorbaría.
        registry.add("application.security.rate-limit.caro-por-minuto", () -> "1000");
        // Cada comprobación recorre: el test rompe la cadena entre una y otra.
        registry.add("application.auditoria.vigencia-del-recorrido", () -> "0s");
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private TimeEntryRepository timeEntryRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final ObjectMapper json = new ObjectMapper();

    private Company alfa;
    private Company beta;
    private long gammaId;
    private User ana;

    private String tokenOperadora;
    private String tokenAdminAlfa;
    private String tokenAdminBeta;
    private String tokenAna;
    private JsonNode sesionOperadora;
    private JsonNode sesionAdminBeta;

    @BeforeAll
    void tresEmpresas() throws Exception {
        Instant ahora = Instant.now();

        // Alfa: cinco en activo (la operadora entre ellos) y uno de baja.
        alfa = companyRepository.save(Company.builder().nombre("Alfa Plataforma").build());
        departmentRepository.save(Department.builder().empresa(alfa).nombre("Almacén").build());
        persona(alfa, "admin.alfa@plataforma.test", "Raúl", "Ortega", Role.ADMIN, true);
        persona(alfa, "gestora@plataforma.test", "Marta", null, Role.GESTOR, true);
        ana = persona(alfa, "ana@plataforma.test", "Ana", "Pérez", Role.EMPLEADO, true);
        User bea = persona(alfa, "bea@plataforma.test", "Bea", null, Role.EMPLEADO, true);
        persona(alfa, OPERADORA, "Olga", "Operadora", Role.EMPLEADO, true);
        persona(alfa, "ido@plataforma.test", "Iván", null, Role.EMPLEADO, false);

        jornada(ana, ahora.minus(2, ChronoUnit.DAYS), false);
        jornada(ana, ahora.minus(10, ChronoUnit.DAYS), false);
        jornada(ana, ahora.minus(40, ChronoUnit.DAYS), false);
        jornada(bea, ahora.minus(3, ChronoUnit.DAYS), false);
        // Anulada: no cuenta en ninguna cifra, por reciente que sea.
        jornada(bea, ahora.minus(1, ChronoUnit.DAYS), true);

        // Beta: dos personas y un fichaje de hace cinco días.
        beta = companyRepository.save(Company.builder().nombre("Beta Plataforma").build());
        persona(beta, "admin.beta@plataforma.test", "Elena", "Ruiz", Role.ADMIN, true);
        User carlos = persona(beta, "carlos@plataforma.test", "Carlos", null, Role.EMPLEADO, true);
        jornada(carlos, ahora.minus(5, ChronoUnit.DAYS), false);

        // Gamma: alguien la registró y nunca confirmó el correo.
        assertThat(post("/auth/register-manager", """
                {"nombreEmpresa":"Gamma a medias","nombre":"Nadie","apellidos":"Todavía",
                 "email":"admin.gamma@plataforma.test","contrasena":"%s"}
                """.formatted(CONTRASENA), null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        gammaId = companyRepository.findByNombre("Gamma a medias").orElseThrow().getId();

        // Las sesiones, por la puerta de verdad: una desde la web y tres desde la app.
        sesionOperadora = entrar(OPERADORA, true);
        tokenOperadora = sesionOperadora.get("token").asText();
        tokenAdminAlfa = entrar("admin.alfa@plataforma.test", false).get("token").asText();
        tokenAna = entrar("ana@plataforma.test", false).get("token").asText();
        sesionAdminBeta = entrar("admin.beta@plataforma.test", false);
        tokenAdminBeta = sesionAdminBeta.get("token").asText();

        // Y un fichaje por la API: es el único que deja fila en la traza de auditoría.
        assertThat(post("/api/v1/fichaje", "{\"tipo\":\"INICIO\"}", bearer(tokenAna)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------
    // A quién se le abre
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La operadora recibe el permiso en su sesión y en su perfil, además de lo de su rol")
    void laOperadoraLoTiene() throws Exception {
        assertThat(authorities(sesionOperadora)).contains("plataforma:ver", "fichaje:escribir")
                .doesNotContain("empleado:gestionar");
        assertThat(sesionOperadora.get("rol").asText()).isEqualTo("EMPLEADO");

        JsonNode perfil = cuerpo(get("/api/v1/perfil", bearer(tokenOperadora)));
        assertThat(authorities(perfil)).contains("plataforma:ver");
    }

    @Test
    @DisplayName("El ADMIN de una empresa no lo recibe: ni en su sesión ni en su perfil")
    void unAdminNoLoRecibe() throws Exception {
        assertThat(authorities(sesionAdminBeta)).contains("empresa:configurar").doesNotContain("plataforma:ver");
        assertThat(authorities(cuerpo(get("/api/v1/perfil", bearer(tokenAdminBeta))))).doesNotContain("plataforma:ver");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    @DisplayName("Cada ruta: 200 a la operadora, 403 a un ADMIN y a una empleada, 401 sin sesión")
    void quienEntraEnCadaRuta(int cual) {
        String ruta = RUTAS.get(cual).replace("/empresas/1", "/empresas/" + alfa.getId());

        assertThat(get(ruta, bearer(tokenOperadora)).getStatusCode()).as(ruta).isEqualTo(HttpStatus.OK);
        // El ADMIN de Alfa pregunta por SU empresa y tampoco: no es cuestión de de quién sea.
        assertThat(get(ruta, bearer(tokenAdminAlfa)).getStatusCode()).as(ruta).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(ruta, bearer(tokenAdminBeta)).getStatusCode()).as(ruta).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(ruta, bearer(tokenAna)).getStatusCode()).as(ruta).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(ruta, null).getStatusCode()).as(ruta).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Un 403 no cuenta nada: ni un nombre ni una cifra de ninguna empresa")
    void el403NoCuentaNada() {
        ResponseEntity<String> respuesta = get("/api/v1/plataforma/empresas", bearer(tokenAdminBeta));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respuesta.getBody()).doesNotContain("Alfa").doesNotContain("Gamma").doesNotContain("contenido");
    }

    // ------------------------------------------------------------------
    // La lista
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La lista trae las tres empresas, cada una con sus cifras y no con las de otra")
    void laListaConLasCifrasDeCadaUna() throws Exception {
        JsonNode pagina = cuerpo(get("/api/v1/plataforma/empresas", bearer(tokenOperadora)));

        assertThat(pagina.get("totalElementos").asLong()).isEqualTo(3);
        assertThat(nombres(pagina)).containsExactly("Alfa Plataforma", "Beta Plataforma", "Gamma a medias");

        JsonNode deAlfa = pagina.get("contenido").get(0);
        assertThat(deAlfa.get("id").asLong()).isEqualTo(alfa.getId());
        assertThat(deAlfa.get("zonaHoraria").asText()).isEqualTo("Europe/Madrid");
        assertThat(deAlfa.get("empleadosActivos").asLong()).isEqualTo(5);
        assertThat(deAlfa.get("empleadosDeBaja").asLong()).isEqualTo(1);
        assertThat(deAlfa.get("registroSinConfirmar").asBoolean()).isFalse();
        // Ana hace 2 días, Bea hace 3 y el de hoy por la API; el de hace 10 entra en los 30.
        assertThat(deAlfa.get("fichajesEn7Dias").asLong()).isEqualTo(3);
        assertThat(deAlfa.get("fichajesEn30Dias").asLong()).isEqualTo(4);
        assertThat(deAlfa.get("personasQueFichan").asLong()).isEqualTo(2);
        assertThat(reciente(deAlfa.get("ultimoFichaje"))).isTrue();
        assertThat(reciente(deAlfa.get("ultimaSesion"))).isTrue();
        assertThat(reciente(deAlfa.get("creadaEn"))).isTrue();

        JsonNode deBeta = pagina.get("contenido").get(1);
        assertThat(deBeta.get("empleadosActivos").asLong()).isEqualTo(2);
        assertThat(deBeta.get("empleadosDeBaja").asLong()).isZero();
        assertThat(deBeta.get("fichajesEn7Dias").asLong()).isEqualTo(1);
        assertThat(deBeta.get("fichajesEn30Dias").asLong()).isEqualTo(1);
        assertThat(deBeta.get("personasQueFichan").asLong()).isEqualTo(1);
        assertThat(Duration.between(Instant.parse(deBeta.get("ultimoFichaje").asText()), Instant.now()).toDays())
                .isEqualTo(5);

        // La que no ha hecho nada sale, con ceros y sin fechas de actividad.
        JsonNode deGamma = pagina.get("contenido").get(2);
        assertThat(deGamma.get("id").asLong()).isEqualTo(gammaId);
        assertThat(deGamma.get("registroSinConfirmar").asBoolean()).isTrue();
        assertThat(deGamma.get("fichajesEn7Dias").asLong()).isZero();
        assertThat(deGamma.get("fichajesEn30Dias").asLong()).isZero();
        assertThat(deGamma.get("personasQueFichan").asLong()).isZero();
        assertThat(deGamma.get("ultimoFichaje").isNull()).isTrue();
        assertThat(deGamma.get("ultimaSesion").isNull()).isTrue();
    }

    @Test
    @DisplayName("Buscar es por un trozo del nombre, sin mayúsculas, y los comodines se buscan tal cual")
    void buscar() throws Exception {
        assertThat(nombres(buscar("BETA"))).containsExactly("Beta Plataforma");
        assertThat(nombres(buscar("plataforma"))).containsExactly("Alfa Plataforma", "Beta Plataforma");
        assertThat(nombres(buscar("  medias "))).containsExactly("Gamma a medias");
        assertThat(nombres(buscar("no-hay-ninguna"))).isEmpty();
        // «%» y «_» en un LIKE lo encontrarían todo.
        assertThat(nombres(buscar("%"))).isEmpty();
        assertThat(nombres(buscar("_"))).isEmpty();
        assertThat(nombres(buscar(""))).hasSize(3);
    }

    @Test
    @DisplayName("Ordenar: por plantilla, por actividad y por alta; lo que no tiene dato, al final")
    void ordenar() throws Exception {
        assertThat(nombres(lista("?orden=EMPLEADOS")))
                .containsExactly("Alfa Plataforma", "Beta Plataforma", "Gamma a medias");
        assertThat(nombres(lista("?orden=ACTIVIDAD")))
                .containsExactly("Alfa Plataforma", "Beta Plataforma", "Gamma a medias");
        // La última en darse de alta, primero.
        assertThat(nombres(lista("?orden=ALTA")))
                .containsExactly("Gamma a medias", "Beta Plataforma", "Alfa Plataforma");
        assertThat(get("/api/v1/plataforma/empresas?orden=INVENTADO", bearer(tokenOperadora)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Paginar: cada página trae las cifras de las suyas, y el total es el de todas")
    void paginar() throws Exception {
        JsonNode segunda = lista("?tamano=2&pagina=1");

        assertThat(nombres(segunda)).containsExactly("Gamma a medias");
        assertThat(segunda.get("totalElementos").asLong()).isEqualTo(3);
        assertThat(segunda.get("totalPaginas").asInt()).isEqualTo(2);
        assertThat(segunda.get("hayMas").asBoolean()).isFalse();

        JsonNode primera = lista("?tamano=1&pagina=1");
        assertThat(nombres(primera)).containsExactly("Beta Plataforma");
        assertThat(primera.get("contenido").get(0).get("fichajesEn30Dias").asLong()).isEqualTo(1);
        assertThat(primera.get("hayMas").asBoolean()).isTrue();

        assertThat(get("/api/v1/plataforma/empresas?tamano=0", bearer(tokenOperadora)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------
    // El detalle
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El detalle de una empresa: plantilla por rol, contacto de sus ADMIN, fichajes, sesiones y recuentos")
    void elDetalle() throws Exception {
        JsonNode detalle = cuerpo(get("/api/v1/plataforma/empresas/" + alfa.getId(), bearer(tokenOperadora)));

        assertThat(detalle.get("nombre").asText()).isEqualTo("Alfa Plataforma");
        assertThat(detalle.get("empleadosActivos").asLong()).isEqualTo(5);
        assertThat(detalle.get("empleadosDeBaja").asLong()).isEqualTo(1);

        // Los cuatro roles, en orden, aunque no haya nadie de RRHH.
        JsonNode plantilla = detalle.get("plantilla");
        assertThat(plantilla).hasSize(4);
        assertThat(rol(plantilla, "EMPLEADO").get("activos").asLong()).isEqualTo(3);
        assertThat(rol(plantilla, "EMPLEADO").get("deBaja").asLong()).isEqualTo(1);
        assertThat(rol(plantilla, "GESTOR").get("activos").asLong()).isEqualTo(1);
        assertThat(rol(plantilla, "RRHH").get("activos").asLong()).isZero();
        assertThat(rol(plantilla, "ADMIN").get("activos").asLong()).isEqualTo(1);

        JsonNode administradores = detalle.get("administradores");
        assertThat(administradores).hasSize(1);
        assertThat(administradores.get(0).get("nombre").asText()).isEqualTo("Raúl Ortega");
        assertThat(administradores.get(0).get("email").asText()).isEqualTo("admin.alfa@plataforma.test");
        assertThat(administradores.get(0).get("correoSinConfirmar").asBoolean()).isFalse();

        JsonNode fichajes = detalle.get("fichajes");
        assertThat(fichajes.get("total").asLong()).isEqualTo(5);
        assertThat(fichajes.get("en7Dias").asLong()).isEqualTo(3);
        assertThat(fichajes.get("en30Dias").asLong()).isEqualTo(4);
        assertThat(fichajes.get("personasEn30Dias").asLong()).isEqualTo(2);
        assertThat(reciente(fichajes.get("ultimo"))).isTrue();

        // La operadora entró desde la web; el ADMIN y Ana, desde la app.
        JsonNode sesiones = detalle.get("sesiones");
        assertThat(sesiones.get("webEn30Dias").asLong()).isEqualTo(1);
        assertThat(sesiones.get("appEn30Dias").asLong()).isEqualTo(2);
        assertThat(reciente(sesiones.get("ultima"))).isTrue();

        assertThat(detalle.get("departamentos").asLong()).isEqualTo(1);
        assertThat(detalle.get("movimientosDeAuditoria").asLong()).isEqualTo(1);
        for (String cero : List.of("cuentasConGoogle", "cuentasConMicrosoft", "kioscos", "dispositivosPush",
                "proyectos", "bytesDeAdjuntos", "borradosPendientes")) {
            assertThat(detalle.get(cero).asLong()).as(cero).isZero();
        }
    }

    @Test
    @DisplayName("El detalle no trae a nadie que no sea ADMIN: ni nombres ni correos de la plantilla")
    void elDetalleNoTraeALaPlantilla() {
        String detalle = get("/api/v1/plataforma/empresas/" + alfa.getId(), bearer(tokenOperadora)).getBody();

        assertThat(detalle).contains("admin.alfa@plataforma.test")
                .doesNotContain("ana@plataforma.test")
                .doesNotContain("gestora@plataforma.test")
                .doesNotContain("Bea")
                .doesNotContain("horaEntrada");
    }

    @Test
    @DisplayName("El detalle de la que se quedó a medias: su ADMIN sin confirmar y todo lo demás a cero")
    void elDetalleDeLaQueSeQuedoAMedias() throws Exception {
        JsonNode detalle = cuerpo(get("/api/v1/plataforma/empresas/" + gammaId, bearer(tokenOperadora)));

        assertThat(detalle.get("administradores")).hasSize(1);
        assertThat(detalle.get("administradores").get(0).get("correoSinConfirmar").asBoolean()).isTrue();
        assertThat(detalle.get("fichajes").get("total").asLong()).isZero();
        assertThat(detalle.get("fichajes").get("ultimo").isNull()).isTrue();
        assertThat(detalle.get("fichajes").get("personasEn30Dias").asLong()).isZero();
        assertThat(detalle.get("sesiones").get("ultima").isNull()).isTrue();
        assertThat(detalle.get("sesiones").get("webEn30Dias").asLong()).isZero();
        assertThat(detalle.get("movimientosDeAuditoria").asLong()).isZero();
        assertThat(reciente(detalle.get("creadaEn"))).isTrue();
    }

    @Test
    @DisplayName("Una empresa que no existe es un 404")
    void unaQueNoExiste() {
        assertThat(get("/api/v1/plataforma/empresas/999999", bearer(tokenOperadora)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // La instalación
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El resumen: totales de la instalación, altas por semana, tareas y la traza")
    void elResumen() throws Exception {
        JsonNode resumen = cuerpo(get("/api/v1/plataforma/resumen", bearer(tokenOperadora)));

        assertThat(resumen.get("empresas").asLong()).isEqualTo(3);
        assertThat(resumen.get("empresasConActividad").asLong()).isEqualTo(2);
        // Cinco de Alfa, dos de Beta y quien registró Gamma.
        assertThat(resumen.get("empleadosActivos").asLong()).isEqualTo(8);
        assertThat(resumen.get("registrosSinConfirmar").asLong()).isEqualTo(1);
        assertThat(resumen.get("fichajesHoy").asLong()).isEqualTo(1);

        // Doce semanas, con sus ceros; las tres altas son de esta.
        JsonNode altas = resumen.get("altasPorSemana");
        assertThat(altas).hasSize(12);
        assertThat(altas.get(11).get("altas").asLong()).isEqualTo(3);
        assertThat(altas.get(0).get("altas").asLong()).isZero();
        assertThat(altas.get(11).get("semana").asText()).isGreaterThan(altas.get(0).get("semana").asText());

        assertThat(resumen.get("tareas").get("tareas")).hasSize(ScheduledTask.values().length);
        // Aquí no ha corrido ninguna noche: no hay punto de control y todo está sin revisar.
        assertThat(resumen.get("cadena").get("ultimaComprobacion").isNull()).isTrue();
        assertThat(resumen.get("cadena").get("movimientosSinRevisar").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("La traza rota en una fila de Alfa: la operadora ve la fila y la empresa; Beta, solo que falla")
    void laIntegridad() throws Exception {
        JsonNode intacta = cuerpo(get("/api/v1/plataforma/integridad", bearer(tokenOperadora)));
        assertThat(intacta.get("intacta").asBoolean()).isTrue();
        assertThat(intacta.get("movimientos").asLong()).isEqualTo(1);
        assertThat(intacta.get("primerFallo").isNull()).isTrue();

        long fila;
        try (Connection duenio = DriverManager.getConnection(testUrl, "nxtime", "nxtime");
             Statement statement = duenio.createStatement()) {
            statement.execute("ALTER TABLE auditoria_fichaje DISABLE TRIGGER USER");
            try {
                statement.execute("UPDATE auditoria_fichaje SET motivo = 'Lo cambie yo'");
            } finally {
                statement.execute("ALTER TABLE auditoria_fichaje ENABLE TRIGGER USER");
            }
            try (var filas = statement.executeQuery("SELECT id FROM auditoria_fichaje")) {
                assertThat(filas.next()).isTrue();
                fila = filas.getLong(1);
            }
        }

        JsonNode rota = cuerpo(get("/api/v1/plataforma/integridad", bearer(tokenOperadora)));
        assertThat(rota.get("intacta").asBoolean()).isFalse();
        assertThat(rota.get("primerFallo").asLong()).isEqualTo(fila);
        assertThat(rota.get("empresaDelFallo").asLong()).isEqualTo(alfa.getId());
        assertThat(rota.get("motivo").asText()).contains("hash");

        // A la empresa que no es la dueña de esa fila se le sigue sin decir cuál es.
        JsonNode vistaPorBeta = cuerpo(get("/api/v1/auditoria/integridad", bearer(tokenAdminBeta)));
        assertThat(vistaPorBeta.get("intacta").asBoolean()).isFalse();
        assertThat(vistaPorBeta.get("primerFallo").isNull()).isTrue();
    }

    // ------------------------------------------------------------------

    private User persona(Company empresa, String email, String nombre, String apellidos, Role rol, boolean activo) {
        return userRepository.save(User.builder()
                .nombre(nombre).apellidos(apellidos).email(email)
                .contrasena(passwordEncoder.encode(CONTRASENA)).rol(rol).empresa(empresa).activo(activo)
                .fechaBaja(activo ? null : Instant.now())
                .build());
    }

    /** Una jornada cerrada de ocho horas que empezó en ese momento. */
    private void jornada(User persona, Instant entrada, boolean anulada) {
        timeEntryRepository.save(TimeEntry.builder()
                .usuario(persona).empresa(persona.getEmpresa())
                .horaEntrada(entrada).horaSalida(entrada.plus(8, ChronoUnit.HOURS))
                .anulado(anulada)
                .build());
    }

    private JsonNode entrar(String email, boolean desdeLaWeb) throws Exception {
        return cuerpo(post("/auth/login", "{\"email\":\"%s\",\"contrasena\":\"%s\"%s}"
                .formatted(email, CONTRASENA, desdeLaWeb ? ",\"origen\":\"WEB\"" : ""), null));
    }

    private JsonNode lista(String consulta) throws Exception {
        return cuerpo(get("/api/v1/plataforma/empresas" + consulta, bearer(tokenOperadora)));
    }

    /** Con el texto como variable de la plantilla: así viaja codificado una vez, no dos. */
    private JsonNode buscar(String texto) throws Exception {
        return cuerpo(rest.exchange("/api/v1/plataforma/empresas?busqueda={texto}", HttpMethod.GET,
                new HttpEntity<>(bearer(tokenOperadora)), String.class, texto));
    }

    private static List<String> nombres(JsonNode pagina) {
        return StreamSupport.stream(pagina.get("contenido").spliterator(), false)
                .map(empresa -> empresa.get("nombre").asText())
                .toList();
    }

    private static List<String> authorities(JsonNode conAuthorities) {
        List<String> resultado = new ArrayList<>();
        conAuthorities.get("authorities").forEach(authority -> resultado.add(authority.asText()));
        return resultado;
    }

    private static JsonNode rol(JsonNode plantilla, String rol) {
        return StreamSupport.stream(plantilla.spliterator(), false)
                .filter(fila -> fila.get("rol").asText().equals(rol))
                .findFirst().orElseThrow();
    }

    /** Una fecha de hace menos de cinco minutos: «ahora», con margen para un test lento. */
    private static boolean reciente(JsonNode fecha) {
        return !fecha.isNull()
                && Duration.between(Instant.parse(fecha.asText()), Instant.now()).abs().toMinutes() < 5;
    }

    private ResponseEntity<String> post(String ruta, String cuerpo, HttpHeaders auth) {
        HttpHeaders cabeceras = auth == null ? new HttpHeaders() : auth;
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
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
}
