package com.nxtime.nxtime.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.RefreshToken;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.notification.EmailSender;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.totp.Totp;
import com.nxtime.nxtime.service.AuthService;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El segundo factor de punta a punta (plan J, fase J8; ADR 051): activarlo,
 * entrar con él, que un código no valga dos veces, los códigos de
 * recuperación, a quién se le exige y qué puede hacer sin él, el SSO como
 * segundo factor, y las contraseñas comunes. Cada regla tiene su test, y cada
 * test usa su propia persona: los códigos van por intervalos de 30 s, y uno
 * gastado en un test no puede valer en el siguiente.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
class SegundoFactorIT {

    private static final String CONTRASENA = "una frase bastante larga";
    private static final String OPERADOR = "operador@segundo-factor.test";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "segundo_factor_it_" + System.nanoTime();
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
        registry.add("application.plataforma.operadores", () -> OPERADOR);
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
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthService authService;

    private final ObjectMapper json = new ObjectMapper();
    private static final AtomicInteger IP = new AtomicInteger(1);
    private static final AtomicInteger N = new AtomicInteger(1);

    // ------------------------------------------------------------------

    private record Activado(User persona, byte[] secreto, List<String> recuperacion) {
    }

    private User persona(Role rol, String correo) {
        Company empresa = companyRepository.save(Company.builder().nombre("Empresa " + N.getAndIncrement()).build());
        return persona(rol, correo, empresa);
    }

    private User persona(Role rol, String correo, Company empresa) {
        return userRepository.save(User.builder().nombre("Ana").apellidos("López").email(correo)
                .contrasena(passwordEncoder.encode(CONTRASENA)).rol(rol).empresa(empresa).activo(true).build());
    }

    private String correo(String quien) {
        return quien + N.getAndIncrement() + "@segundo-factor.test";
    }

    private HttpHeaders cabeceras(String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        int n = IP.getAndIncrement();
        cabeceras.set("X-Forwarded-For", "198.51." + (n / 250) + "." + (n % 250 + 1));
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

    private ResponseEntity<String> segundoPaso(String desafio, String codigo) {
        return post("/auth/2fa", null, "{\"desafio\":\"%s\",\"codigo\":\"%s\"}".formatted(desafio, codigo));
    }

    private JsonNode cuerpo(ResponseEntity<String> respuesta) {
        return json.readTree(respuesta.getBody());
    }

    private String token(ResponseEntity<String> sesion) {
        assertThat(sesion.getStatusCode().value()).as(sesion.getBody()).isEqualTo(200);
        return cuerpo(sesion).get("token").asText();
    }

    /** Entra sin segundo factor: devuelve el access token. */
    private String tokenSinSegundoFactor(String correo) {
        return token(entrar(correo));
    }

    private static String codigo(byte[] secreto, int pasos) {
        return Totp.codigo(secreto, Totp.pasoDe(Instant.now()) + pasos);
    }

    /** Lo activa por la API, como lo haría la web, y devuelve el secreto del QR y los códigos. */
    private Activado activar(User persona) {
        String token = tokenSinSegundoFactor(persona.getEmail());
        ResponseEntity<String> inicio = post("/api/v1/perfil/segundo-factor/activacion", token, "");
        assertThat(inicio.getStatusCode().value()).as(inicio.getBody()).isEqualTo(200);
        byte[] secreto = deBase32(cuerpo(inicio).get("secreto").asText());
        assertThat(cuerpo(inicio).get("uri").asText()).startsWith("otpauth://totp/NX%20Time:");

        ResponseEntity<String> confirmado = post("/api/v1/perfil/segundo-factor/activacion/confirmar", token,
                "{\"contrasena\":\"%s\",\"codigo\":\"%s\"}".formatted(CONTRASENA, codigo(secreto, 0)));
        assertThat(confirmado.getStatusCode().value()).as(confirmado.getBody()).isEqualTo(200);
        List<String> recuperacion = new ArrayList<>();
        cuerpo(confirmado).get("codigos").forEach(nodo -> recuperacion.add(nodo.asText()));
        assertThat(recuperacion).hasSize(10);
        return new Activado(userRepository.findById(persona.getId()).orElseThrow(), secreto, recuperacion);
    }

    /** El desafío del login de quien tiene segundo factor. */
    private String desafio(String correo) {
        ResponseEntity<String> login = entrar(correo);
        assertThat(login.getStatusCode().value()).as(login.getBody()).isEqualTo(200);
        assertThat(cuerpo(login).path("token").isNull()).as("sin token hasta el código").isTrue();
        return cuerpo(login).get("desafio").asText();
    }

    private static byte[] deBase32(String texto) {
        String alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : texto.toCharArray()) {
            buffer = (buffer << 5) | alfabeto.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                salida.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return salida.toByteArray();
    }

    private String codigoDeError(ResponseEntity<String> respuesta) {
        return cuerpo(respuesta).path("codigo").asText();
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("Con el segundo factor activo, la contraseña no abre sesión: hace falta el código")
    void loginEnDosPasos() {
        Activado ana = activar(persona(Role.EMPLEADO, correo("ana")));

        ResponseEntity<String> login = entrar(ana.persona().getEmail());
        assertThat(cuerpo(login).path("token").isNull()).isTrue();
        assertThat(cuerpo(login).path("refreshToken").isNull()).isTrue();
        assertThat(login.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        String desafio = cuerpo(login).get("desafio").asText();
        // El desafío no es un access token: no entra en la API.
        assertThat(get("/api/v1/perfil", desafio).getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> sesion = segundoPaso(desafio, codigo(ana.secreto(), 1));
        assertThat(get("/api/v1/perfil", token(sesion)).getStatusCode().value()).isEqualTo(200);
        assertThat(cuerpo(sesion).path("desafio").isNull()).isTrue();
    }

    @Test
    @DisplayName("Un código ya usado no vuelve a valer, aunque siga dentro de los 30 s")
    void codigoUsadoNoVale() {
        Activado luis = activar(persona(Role.EMPLEADO, correo("luis")));
        String codigo = codigo(luis.secreto(), 1);
        assertThat(segundoPaso(desafio(luis.persona().getEmail()), codigo).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> otraVez = segundoPaso(desafio(luis.persona().getEmail()), codigo);
        assertThat(otraVez.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("El código de hace un minuto ya no vale (fuera de la ventana)")
    void fueraDeVentana() {
        Activado eva = activar(persona(Role.EMPLEADO, correo("eva")));
        ResponseEntity<String> viejo = segundoPaso(desafio(eva.persona().getEmail()), codigo(eva.secreto(), -3));
        assertThat(viejo.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("Un código de recuperación vale una vez, como se copie, y avisa por correo")
    void recuperacionDeUnSoloUso() {
        Activado pedro = activar(persona(Role.EMPLEADO, correo("pedro")));
        String deRecuperacion = pedro.recuperacion().get(3);

        ResponseEntity<String> primera = segundoPaso(desafio(pedro.persona().getEmail()),
                deRecuperacion.toUpperCase().replace("-", ""));
        assertThat(primera.getStatusCode().value()).as(primera.getBody()).isEqualTo(200);
        ResponseEntity<String> segunda = segundoPaso(desafio(pedro.persona().getEmail()), deRecuperacion);
        assertThat(segunda.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<String> estado = get("/api/v1/perfil/segundo-factor", token(primera));
        assertThat(cuerpo(estado).get("codigosDeRecuperacion").asInt()).isEqualTo(9);
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.timeout(5000)).enviar(
                org.mockito.ArgumentMatchers.eq(pedro.persona().getEmail()), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("second-factor"),
                org.mockito.ArgumentMatchers.argThat(variables -> "RECUPERACION".equals(variables.get("que"))));
    }

    @Test
    @DisplayName("Un desafío manipulado, o de antes de cambiar la contraseña, no vale")
    void desafioQueYaNoVale() {
        Activado marta = activar(persona(Role.EMPLEADO, correo("marta")));
        String desafio = desafio(marta.persona().getEmail());

        assertThat(segundoPaso(desafio + "x", codigo(marta.secreto(), 1)).getStatusCode().value()).isEqualTo(401);

        User conOtra = userRepository.findById(marta.persona().getId()).orElseThrow();
        conOtra.setContrasena(passwordEncoder.encode("otra frase distinta y larga"));
        userRepository.save(conOtra);
        assertThat(segundoPaso(desafio, codigo(marta.secreto(), 1)).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("Los fallos de código cuentan aparte: acertar la contraseña no los pone a cero")
    void limiteDeCodigosAparte() {
        Activado raul = activar(persona(Role.EMPLEADO, correo("raul")));
        // Cada intento, con un login nuevo por delante: la contraseña acierta
        // siempre. Si eso pusiera a cero los fallos de código, no saldría
        // nunca un 429. No se mira en qué intento sale: la espera tras el
        // quinto fallo es de un segundo, y cuánto tarda cada vuelta depende de
        // la máquina (la trampa de siempre con este limitador). Siete vueltas
        // caben en el límite de diez entradas por minuto de la contraseña.
        List<Integer> respuestas = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            respuestas.add(segundoPaso(desafio(raul.persona().getEmail()), "000000").getStatusCode().value());
        }
        assertThat(respuestas.subList(0, 5)).containsOnly(401);
        assertThat(respuestas).contains(429);
    }

    @Test
    @DisplayName("Al operador de plataforma se le exige: sin él, solo puede activarlo; activado, ya entra")
    void operadorObligado() {
        User operador = userRepository.findByEmail(OPERADOR).orElseGet(() -> persona(Role.ADMIN, OPERADOR));
        ResponseEntity<String> login = entrar(OPERADOR);
        assertThat(cuerpo(login).get("segundoFactorPorActivar").asBoolean()).isTrue();
        String token = token(login);

        ResponseEntity<String> panel = get("/api/v1/plataforma/resumen", token);
        assertThat(panel.getStatusCode().value()).isEqualTo(403);
        assertThat(codigoDeError(panel)).isEqualTo("SEGUNDO_FACTOR_OBLIGATORIO");
        // Lo de fichar tampoco: solo activarlo.
        assertThat(codigoDeError(get("/api/v1/fichaje/hoy", token))).isEqualTo("SEGUNDO_FACTOR_OBLIGATORIO");
        assertThat(get("/api/v1/perfil", token).getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> estado = get("/api/v1/perfil/segundo-factor", token);
        assertThat(cuerpo(estado).get("obligatorio").asBoolean()).isTrue();
        assertThat(cuerpo(estado).get("motivo").asText()).contains("operador");

        Activado activado = activar(operador);
        ResponseEntity<String> conCodigo = segundoPaso(desafio(OPERADOR), codigo(activado.secreto(), 1));
        assertThat(cuerpo(conCodigo).get("segundoFactorPorActivar").asBoolean()).isFalse();
        assertThat(get("/api/v1/plataforma/resumen", token(conCodigo)).getStatusCode().value()).isEqualTo(200);
        // Y no lo puede quitar.
        ResponseEntity<String> quitar = post("/api/v1/perfil/segundo-factor/desactivar", token(conCodigo),
                "{\"contrasena\":\"%s\",\"codigo\":\"%s\"}".formatted(CONTRASENA, activado.recuperacion().get(0)));
        assertThat(quitar.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("La sesión desde la que se activa deja de estar limitada al renovar el token")
    void laSesionQueLoActivaPasaAContar() {
        Company empresa = companyRepository.save(Company.builder().nombre("Con canal " + N.getAndIncrement()).build());
        User responsable = persona(Role.EMPLEADO, correo("responsable"), empresa);
        empresa.setResponsableCanalId(responsable.getId());
        companyRepository.save(empresa);

        ResponseEntity<String> login = post("/auth/login", null, "{\"email\":\"%s\",\"contrasena\":\"%s\",\"origen\":\"ANDROID\"}"
                .formatted(responsable.getEmail(), CONTRASENA));
        String token = token(login);
        String refresh = cuerpo(login).get("refreshToken").asText();
        assertThat(codigoDeError(get("/api/v1/denuncias", token))).isEqualTo("SEGUNDO_FACTOR_OBLIGATORIO");

        ResponseEntity<String> inicio = post("/api/v1/perfil/segundo-factor/activacion", token, "");
        byte[] secreto = deBase32(cuerpo(inicio).get("secreto").asText());
        assertThat(post("/api/v1/perfil/segundo-factor/activacion/confirmar", token,
                "{\"contrasena\":\"%s\",\"codigo\":\"%s\"}".formatted(CONTRASENA, codigo(secreto, 0)))
                .getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> renovado = post("/auth/refresh", null, "{\"refreshToken\":\"%s\"}".formatted(refresh));
        assertThat(cuerpo(renovado).get("segundoFactorPorActivar").asBoolean()).isFalse();
        ResponseEntity<String> instruir = get("/api/v1/denuncias", token(renovado));
        assertThat(instruir.getStatusCode().value()).as(instruir.getBody()).isEqualTo(200);
    }

    @Test
    @DisplayName("Entrar con Google o Microsoft cuenta como segundo factor: no se le limita ni se le pide código")
    void ssoCuentaComoSegundoFactor() {
        User operador = userRepository.findByEmail(OPERADOR).orElseGet(() -> persona(Role.ADMIN, OPERADOR));
        String token = authService.abrirSesion(operador.getId(), RefreshToken.Origen.WEB).token();
        assertThat(get("/api/v1/plataforma/resumen", token).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("Una empresa puede exigirlo a ADMIN y RRHH, pero solo si quien lo exige ya lo tiene")
    void empresaQueLoExige() {
        Company empresa = companyRepository.save(Company.builder().nombre("Exigente " + N.getAndIncrement()).build());
        User admin = persona(Role.ADMIN, correo("admin"), empresa);
        User rrhh = persona(Role.RRHH, correo("rrhh"), empresa);
        User empleado = persona(Role.EMPLEADO, correo("empleado"), empresa);
        String ajustes = "{\"nombre\":\"%s\",\"zonaHoraria\":\"Europe/Madrid\",\"exigirSegundoFactor\":true}"
                .formatted(empresa.getNombre());

        ResponseEntity<String> sinTenerlo = rest.exchange("/api/v1/empresa/ajustes", HttpMethod.PUT,
                new HttpEntity<>(ajustes, cabeceras(tokenSinSegundoFactor(admin.getEmail()))), String.class);
        assertThat(sinTenerlo.getStatusCode().value()).isEqualTo(409);

        Activado conEl = activar(admin);
        String tokenAdmin = token(segundoPaso(desafio(admin.getEmail()), codigo(conEl.secreto(), 1)));
        ResponseEntity<String> exigido = rest.exchange("/api/v1/empresa/ajustes", HttpMethod.PUT,
                new HttpEntity<>(ajustes, cabeceras(tokenAdmin)), String.class);
        assertThat(exigido.getStatusCode().value()).as(exigido.getBody()).isEqualTo(200);

        assertThat(cuerpo(entrar(rrhh.getEmail())).get("segundoFactorPorActivar").asBoolean()).isTrue();
        assertThat(cuerpo(entrar(empleado.getEmail())).get("segundoFactorPorActivar").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Un ADMIN se lo quita a quien ha perdido el móvil; RRHH no a un ADMIN, y nadie a sí mismo")
    void quitarloAOtro() {
        Company empresa = companyRepository.save(Company.builder().nombre("Móvil perdido " + N.getAndIncrement()).build());
        User admin = persona(Role.ADMIN, correo("admin"), empresa);
        User rrhh = persona(Role.RRHH, correo("rrhh"), empresa);
        User empleado = persona(Role.EMPLEADO, correo("empleado"), empresa);
        activar(empleado);
        Activado adminActivado = activar(admin);
        String tokenAdmin = token(segundoPaso(desafio(admin.getEmail()), codigo(adminActivado.secreto(), 1)));
        String tokenRrhh = tokenSinSegundoFactor(rrhh.getEmail());

        assertThat(rest.exchange("/api/v1/gestor/empleados/" + admin.getId() + "/segundo-factor", HttpMethod.DELETE,
                new HttpEntity<>(cabeceras(tokenRrhh)), String.class).getStatusCode().value()).isEqualTo(403);
        assertThat(rest.exchange("/api/v1/gestor/empleados/" + admin.getId() + "/segundo-factor", HttpMethod.DELETE,
                new HttpEntity<>(cabeceras(tokenAdmin)), String.class).getStatusCode().value()).isEqualTo(403);
        assertThat(rest.exchange("/api/v1/gestor/empleados/" + empleado.getId() + "/segundo-factor", HttpMethod.DELETE,
                new HttpEntity<>(cabeceras(tokenAdmin)), String.class).getStatusCode().value()).isEqualTo(204);

        // Ya entra solo con la contraseña.
        assertThat(cuerpo(entrar(empleado.getEmail())).get("token").isTextual()).isTrue();
    }

    @Test
    @DisplayName("«Cerrar todas las sesiones» corta también el access token que ya tenía")
    void cerrarSesionesCortaElAccessToken() {
        User sara = persona(Role.EMPLEADO, correo("sara"));
        String token = tokenSinSegundoFactor(sara.getEmail());
        assertThat(get("/api/v1/perfil", token).getStatusCode().value()).isEqualTo(200);

        assertThat(post("/api/v1/usuario/cerrar-sesiones", token, "").getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/v1/perfil", token).getStatusCode().value()).isEqualTo(401);
        // Una sesión nueva, después, sí vale.
        assertThat(get("/api/v1/perfil", tokenSinSegundoFactor(sara.getEmail())).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("Una contraseña de las más usadas no se acepta al cambiarla")
    void contrasenaComun() {
        User tomas = persona(Role.EMPLEADO, correo("tomas"));
        ResponseEntity<String> cambio = post("/api/v1/usuario/cambiar-contrasena", tokenSinSegundoFactor(tomas.getEmail()),
                "{\"contrasenaAntigua\":\"%s\",\"contrasenaNueva\":\"Password123\"}".formatted(CONTRASENA));
        assertThat(cambio.getStatusCode().value()).isEqualTo(400);
        assertThat(cuerpo(cambio).path("detail").asText()).contains("de las más usadas");
    }

    @Test
    @DisplayName("Empezar a activarlo dos veces a la vez no choca (la web lo hacía con el modo estricto de React)")
    void empezarDosVecesALaVez() throws Exception {
        User olga = persona(Role.EMPLEADO, correo("olga"));
        String token = tokenSinSegundoFactor(olga.getEmail());
        java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<Integer>> respuestas = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                respuestas.add(hilos.submit(() ->
                        post("/api/v1/perfil/segundo-factor/activacion", token, "").getStatusCode().value()));
            }
            for (java.util.concurrent.Future<Integer> respuesta : respuestas) {
                assertThat(respuesta.get()).isEqualTo(200);
            }
        } finally {
            hilos.shutdown();
        }
        // Y una vez activo, empezar otra vez no lo pisa.
        activar(userRepository.findById(olga.getId()).orElseThrow());
        assertThat(post("/api/v1/perfil/segundo-factor/activacion", token, "").getStatusCode().value()).isEqualTo(409);
    }

    @Test
    @DisplayName("Activarlo pide la contraseña: con una sesión robada no basta")
    void activarPideLaContrasena() {
        User ines = persona(Role.EMPLEADO, correo("ines"));
        String token = tokenSinSegundoFactor(ines.getEmail());
        byte[] secreto = deBase32(cuerpo(post("/api/v1/perfil/segundo-factor/activacion", token, "")).get("secreto").asText());
        ResponseEntity<String> sinContrasena = post("/api/v1/perfil/segundo-factor/activacion/confirmar", token,
                "{\"contrasena\":\"no es la suya\",\"codigo\":\"%s\"}".formatted(codigo(secreto, 0)));
        assertThat(sinContrasena.getStatusCode().value()).isEqualTo(400);
        assertThat(userRepository.findById(ines.getId()).orElseThrow().tieneSegundoFactor()).isFalse();
    }
}
