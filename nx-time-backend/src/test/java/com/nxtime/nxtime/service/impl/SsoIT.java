package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.sso.AppExchangeCodes;
import com.nxtime.nxtime.support.ProveedorOidcDeMentira;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Entrar con Google o con Microsoft (ADR 036), de punta a punta: el servidor
 * arrancado de verdad, PostgreSQL real y un proveedor de mentira
 * ({@link ProveedorOidcDeMentira}) al que el servidor llama por HTTP como
 * llamaría a Google.
 *
 * El test hace de navegador: pide la ida, se queda con la cookie y con lo que
 * el servidor mandaría al proveedor, y vuelve con un código. No sigue
 * redirecciones: lo que se comprueba es adónde manda cada una.
 *
 * <b>La mitad de estos tests son de cosas que no deben funcionar.</b> Un SSO
 * que deja entrar es fácil de probar a mano; lo que no se prueba a mano es que
 * NO deje entrar con un token para otra aplicación, de otro emisor, con el
 * nonce de otra ida o con el correo sin garantizar.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SsoIT {

    private static final ProveedorOidcDeMentira PROVEEDOR = new ProveedorOidcDeMentira();

    private static final String API = "https://api.sso.test";
    private static final String WEB = "https://web.sso.test";
    private static final String ID_GOOGLE = "cliente-de-google";
    private static final String ID_MICROSOFT = "cliente-de-microsoft";
    private static final String INQUILINO = "11111111-2222-3333-4444-555555555555";
    private static final String INQUILINO_PERSONAL = "9188040d-6c67-4c5b-b112-36a304b66dad";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "sso_it_" + System.nanoTime();
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

        String sso = "application.security.sso.";
        registry.add(sso + "url-publica", () -> API);
        registry.add(sso + "url-web", () -> WEB);
        registry.add(sso + "google.client-id", () -> ID_GOOGLE);
        registry.add(sso + "google.client-secret", () -> "secreto-de-google");
        registry.add(sso + "google.authorization-uri", () -> PROVEEDOR.url("/google/autorizar"));
        registry.add(sso + "google.token-uri", () -> PROVEEDOR.url("/token"));
        registry.add(sso + "google.jwk-set-uri", () -> PROVEEDOR.url("/jwks"));
        registry.add(sso + "microsoft.client-id", () -> ID_MICROSOFT);
        registry.add(sso + "microsoft.client-secret", () -> "secreto-de-microsoft");
        registry.add(sso + "microsoft.authorization-uri", () -> PROVEEDOR.url("/microsoft/autorizar"));
        registry.add(sso + "microsoft.token-uri", () -> PROVEEDOR.url("/token"));
        registry.add(sso + "microsoft.jwk-set-uri", () -> PROVEEDOR.url("/jwks"));
        // Los emisores son los de verdad (application.yml): el de mentira firma diciendo ser ellos.
    }

    @AfterAll
    static void apagar() {
        PROVEEDOR.close();
    }

    @LocalServerPort
    private int puerto;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PersonalDataEraser eraser;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private final HttpClient navegador = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    /** Cada petición, desde «otra IP»: el límite de intentos por IP no es lo que se prueba aquí. */
    private static final AtomicInteger IP = new AtomicInteger(1);

    private Company empresa;

    @BeforeEach
    void preparar() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa SSO " + System.nanoTime()).build());
        PROVEEDOR.olvidarCanjes();
    }

    // ------------------------------------------------------------------
    // Lo que hace falta para hacer de navegador
    // ------------------------------------------------------------------

    private User persona(String nombre) {
        return userRepository.save(User.builder()
                .nombre(nombre).apellidos("Pruebas").email(nombre.toLowerCase() + System.nanoTime() + "@sso.test")
                .contrasena(passwordEncoder.encode("password123")).rol(Role.EMPLEADO).empresa(empresa).activo(true)
                .build());
    }

    private HttpRequest.Builder peticion(String ruta) {
        int n = IP.getAndIncrement();
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("X-Forwarded-For", "198.51." + (n / 250) + "." + (n % 250 + 1));
    }

    private HttpResponse<String> get(String ruta, String cookies) throws Exception {
        HttpRequest.Builder peticion = peticion(ruta).GET();
        if (cookies != null) {
            peticion.header("Cookie", cookies);
        }
        return navegador.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String ruta, Object cuerpo, String... cabeceras) throws Exception {
        HttpRequest.Builder peticion = peticion(ruta)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)));
        for (int i = 0; i < cabeceras.length; i += 2) {
            peticion.header(cabeceras[i], cabeceras[i + 1]);
        }
        return navegador.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** El valor de una cookie que pone la respuesta, o null. Vacío si la borra. */
    private static String cookie(HttpResponse<?> respuesta, String nombre) {
        return respuesta.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(nombre + "="))
                .map(c -> c.substring(nombre.length() + 1, c.contains(";") ? c.indexOf(';') : c.length()))
                .findFirst().orElse(null);
    }

    private static String atributos(HttpResponse<?> respuesta, String nombre) {
        return respuesta.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(nombre + "=")).findFirst().orElse("");
    }

    private static String destino(HttpResponse<?> respuesta) {
        return respuesta.headers().firstValue("Location").orElse(null);
    }

    private static Map<String, String> parametros(String url) {
        Map<String, String> resultado = new HashMap<>();
        String consulta = url.contains("?") ? url.substring(url.indexOf('?') + 1) : "";
        for (String par : consulta.split("&")) {
            int igual = par.indexOf('=');
            if (igual > 0) {
                resultado.put(par.substring(0, igual), URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8));
            }
        }
        return resultado;
    }

    /** Lo que queda en manos del navegador tras pedir la ida. */
    private record Ida(String cookie, Map<String, String> alProveedor, String url) {
        String state() {
            return alProveedor.get("state");
        }

        String nonce() {
            return alProveedor.get("nonce");
        }
    }

    private Ida iniciar(String proveedor, String consulta, String cookies) throws Exception {
        HttpResponse<String> respuesta = get("/auth/sso/" + proveedor + "/iniciar" + consulta, cookies);
        assertThat(respuesta.statusCode()).isEqualTo(302);
        return new Ida(cookie(respuesta, "nx_sso"), parametros(destino(respuesta)), destino(respuesta));
    }

    private HttpResponse<String> volver(String proveedor, Ida ida) throws Exception {
        return get("/auth/sso/" + proveedor + "/vuelta?code=un-codigo&state=" + ida.state(), "nx_sso=" + ida.cookie());
    }

    /** Un ID token de Google como los buenos, para la ida dada. Cada test tuerce lo que le interesa. */
    private static Map<String, Object> deGoogle(Ida ida, String sujeto, String correo) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "https://accounts.google.com");
        claims.put("aud", ID_GOOGLE);
        claims.put("sub", sujeto);
        claims.put("email", correo);
        claims.put("email_verified", true);
        claims.put("nonce", ida.nonce());
        return claims;
    }

    private static Map<String, Object> deMicrosoft(Ida ida, String sujeto, String correo, String inquilino) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "https://login.microsoftonline.com/" + inquilino + "/v2.0");
        claims.put("aud", ID_MICROSOFT);
        claims.put("tid", inquilino);
        claims.put("sub", sujeto);
        claims.put("email", correo);
        claims.put("nonce", ida.nonce());
        return claims;
    }

    private static String sujeto() {
        return "sujeto-" + System.nanoTime();
    }

    /** Entra por la web con Google y devuelve la respuesta de la vuelta. */
    private HttpResponse<String> entrarConGoogle(String sujeto, String correo) throws Exception {
        Ida ida = iniciar("google", "", null);
        PROVEEDOR.proximoToken(deGoogle(ida, sujeto, correo));
        return volver("google", ida);
    }

    private int identidadesDe(User usuario) {
        Integer cuantas = jdbc.queryForObject(
                "SELECT count(*) FROM identidades_externas WHERE usuario_id = ?", Integer.class, usuario.getId());
        return cuantas == null ? 0 : cuantas;
    }

    private void noHaEntrado(HttpResponse<String> respuesta, String motivo) {
        assertThat(respuesta.statusCode()).isEqualTo(302);
        assertThat(destino(respuesta)).isEqualTo(WEB + "/?sso=" + motivo);
        assertThat(cookie(respuesta, "nx_refresh")).as("sin sesión").isNull();
    }

    // ------------------------------------------------------------------
    // Qué hay
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Los proveedores configurados se anuncian, con la URL pública de inicio")
    void proveedores() throws Exception {
        JsonNode lista = json.readTree(get("/auth/sso/proveedores", null).body());

        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).get("id").asText()).isEqualTo("google");
        assertThat(lista.get(0).get("nombre").asText()).isEqualTo("Google");
        assertThat(lista.get(0).get("inicio").asText()).isEqualTo(API + "/auth/sso/google/iniciar");
        assertThat(lista.get(1).get("id").asText()).isEqualTo("microsoft");
    }

    @Test
    @DisplayName("Un proveedor que no existe vuelve a la web diciendo que no está disponible")
    void proveedorDesconocido() throws Exception {
        HttpResponse<String> respuesta = get("/auth/sso/facebook/iniciar", null);

        assertThat(respuesta.statusCode()).isEqualTo(302);
        assertThat(destino(respuesta)).isEqualTo(WEB + "/?sso=no-disponible");
    }

    // ------------------------------------------------------------------
    // Entrar desde la web
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La ida manda al proveedor con PKCE, y la cookie de estado es HttpOnly, Secure y Lax")
    void laIda() throws Exception {
        HttpResponse<String> respuesta = get("/auth/sso/google/iniciar", null);
        Map<String, String> alProveedor = parametros(destino(respuesta));

        assertThat(destino(respuesta)).startsWith(PROVEEDOR.url("/google/autorizar"));
        assertThat(alProveedor)
                .containsEntry("response_type", "code")
                .containsEntry("client_id", ID_GOOGLE)
                .containsEntry("redirect_uri", API + "/auth/sso/google/vuelta")
                .containsEntry("scope", "openid email profile")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("response_mode", "query");
        assertThat(alProveedor.get("state")).hasSize(43);
        assertThat(alProveedor.get("nonce")).hasSize(43).isNotEqualTo(alProveedor.get("state"));
        assertThat(alProveedor.get("code_challenge")).hasSize(43);
        // Lax y no Strict: la vuelta empieza en el proveedor, y con Strict no viajaría.
        assertThat(atributos(respuesta, "nx_sso"))
                .contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/auth/sso");
        assertThat(respuesta.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    @Test
    @DisplayName("Con el correo verificado de alguien que ya tiene cuenta: entra, queda vinculada y la sesión es de verdad")
    void entraPorLaWeb() throws Exception {
        User ana = persona("Ana");
        String sujeto = sujeto();
        Ida ida = iniciar("google", "", null);
        PROVEEDOR.proximoToken(deGoogle(ida, sujeto, ana.getEmail()));

        HttpResponse<String> vuelta = volver("google", ida);

        assertThat(vuelta.statusCode()).isEqualTo(302);
        assertThat(destino(vuelta)).isEqualTo(WEB + "/");
        // Las mismas cookies que tras un login con contraseña.
        String refresh = cookie(vuelta, "nx_refresh");
        String csrf = cookie(vuelta, "nx_csrf");
        assertThat(refresh).isNotBlank();
        assertThat(atributos(vuelta, "nx_refresh")).contains("HttpOnly").contains("SameSite=Strict");
        // La de estado se ha gastado.
        assertThat(cookie(vuelta, "nx_sso")).isEmpty();
        assertThat(identidadesDe(ana)).isEqualTo(1);

        // Al proveedor se le canjeó con el secreto, la URL de vuelta y el
        // verificador que corresponde al reto que se le mandó en la ida.
        Map<String, String> canje = PROVEEDOR.canjes().get(0);
        assertThat(canje)
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "un-codigo")
                .containsEntry("client_id", ID_GOOGLE)
                .containsEntry("client_secret", "secreto-de-google")
                .containsEntry("redirect_uri", API + "/auth/sso/google/vuelta");
        assertThat(AppExchangeCodes.retoDe(canje.get("code_verifier"))).isEqualTo(ida.alProveedor().get("code_challenge"));

        // Y con esas cookies la web recupera la sesión, como al recargar.
        HttpResponse<String> sesion = post("/auth/refresh", Map.of(),
                "Cookie", "nx_refresh=" + refresh + "; nx_csrf=" + csrf, "X-CSRF-Token", csrf);
        assertThat(sesion.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(sesion.body());
        assertThat(cuerpo.get("token").asText()).isNotBlank();
        assertThat(cuerpo.get("nombre").asText()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Desde la segunda vez manda el sujeto: entra aunque el correo haya cambiado y ya no esté verificado")
    void laSegundaVezPorElSujeto() throws Exception {
        User ana = persona("Ana");
        String sujeto = sujeto();
        assertThat(cookie(entrarConGoogle(sujeto, ana.getEmail()), "nx_refresh")).isNotBlank();

        Ida ida = iniciar("google", "", null);
        Map<String, Object> claims = deGoogle(ida, sujeto, "otro-correo-" + System.nanoTime() + "@sso.test");
        claims.put("email_verified", false);
        PROVEEDOR.proximoToken(claims);
        HttpResponse<String> vuelta = volver("google", ida);

        assertThat(destino(vuelta)).isEqualTo(WEB + "/");
        assertThat(cookie(vuelta, "nx_refresh")).isNotBlank();
        assertThat(identidadesDe(ana)).isEqualTo(1);
    }

    @Test
    @DisplayName("Quien hereda el correo de otro en el proveedor NO entra en su cuenta: ya tiene vinculada otra")
    void elCorreoHeredadoNoAbreLaCuentaDeOtro() throws Exception {
        User ana = persona("Ana");
        assertThat(cookie(entrarConGoogle(sujeto(), ana.getEmail()), "nx_refresh")).isNotBlank();

        // Otra cuenta de Google, distinta, que ahora tiene ese mismo correo.
        HttpResponse<String> vuelta = entrarConGoogle(sujeto(), ana.getEmail());

        noHaEntrado(vuelta, "ya-tiene-otra");
        assertThat(identidadesDe(ana)).isEqualTo(1);
    }

    @Test
    @DisplayName("Sin cuenta en NX Time no se entra ni se crea nada")
    void sinCuenta() throws Exception {
        long antes = userRepository.count();

        HttpResponse<String> vuelta = entrarConGoogle(sujeto(), "nadie-" + System.nanoTime() + "@sso.test");

        noHaEntrado(vuelta, "sin-cuenta");
        assertThat(userRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Con el correo sin verificar no se busca la cuenta por él")
    void correoSinVerificar() throws Exception {
        User ana = persona("Ana");
        Ida ida = iniciar("google", "", null);
        Map<String, Object> claims = deGoogle(ida, sujeto(), ana.getEmail());
        claims.put("email_verified", false);
        PROVEEDOR.proximoToken(claims);

        noHaEntrado(volver("google", ida), "correo-sin-verificar");
        assertThat(identidadesDe(ana)).isZero();
    }

    @Test
    @DisplayName("Una cuenta dada de baja no entra, ni por el correo ni por el sujeto ya vinculado")
    void cuentaInactiva() throws Exception {
        User ana = persona("Ana");
        String sujeto = sujeto();
        assertThat(cookie(entrarConGoogle(sujeto, ana.getEmail()), "nx_refresh")).isNotBlank();
        jdbc.update("UPDATE usuarios SET activo = FALSE WHERE id = ?", ana.getId());

        noHaEntrado(entrarConGoogle(sujeto, ana.getEmail()), "cuenta-inactiva");

        User javi = persona("Javi");
        jdbc.update("UPDATE usuarios SET activo = FALSE WHERE id = ?", javi.getId());
        noHaEntrado(entrarConGoogle(sujeto(), javi.getEmail()), "cuenta-inactiva");
        assertThat(identidadesDe(javi)).isZero();
    }

    @Test
    @DisplayName("Quien registró su empresa y no había confirmado el correo lo confirma entrando con esa cuenta")
    void confirmaElCorreoPendiente() throws Exception {
        User ana = persona("Ana");
        jdbc.update("UPDATE usuarios SET correo_sin_confirmar_desde = NOW() WHERE id = ?", ana.getId());

        HttpResponse<String> vuelta = entrarConGoogle(sujeto(), ana.getEmail());

        assertThat(cookie(vuelta, "nx_refresh")).isNotBlank();
        assertThat(userRepository.findById(ana.getId()).orElseThrow().correoPendienteDeConfirmar()).isFalse();
    }

    @Test
    @DisplayName("Si la persona cancela en el proveedor, vuelve a la web sabiéndolo y no se canjea nada")
    void cancelado() throws Exception {
        Ida ida = iniciar("google", "", null);

        HttpResponse<String> vuelta = get(
                "/auth/sso/google/vuelta?error=access_denied&state=" + ida.state(), "nx_sso=" + ida.cookie());

        noHaEntrado(vuelta, "cancelado");
        assertThat(PROVEEDOR.canjes()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Lo que NO debe colar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una vuelta que no es de ninguna ida no llega ni a canjear: sin cookie, con otro state, con la cookie de otro proveedor")
    void laVueltaTieneQueSerDeUnaIda() throws Exception {
        Ida ida = iniciar("google", "", null);
        PROVEEDOR.proximoToken(deGoogle(ida, sujeto(), persona("Ana").getEmail()));

        noHaEntrado(get("/auth/sso/google/vuelta?code=un-codigo&state=" + ida.state(), null), "fallo");
        noHaEntrado(get("/auth/sso/google/vuelta?code=un-codigo&state=otro", "nx_sso=" + ida.cookie()), "fallo");
        noHaEntrado(get("/auth/sso/google/vuelta?code=un-codigo", "nx_sso=" + ida.cookie()), "fallo");
        noHaEntrado(get("/auth/sso/microsoft/vuelta?code=un-codigo&state=" + ida.state(), "nx_sso=" + ida.cookie()), "fallo");

        assertThat(PROVEEDOR.canjes()).isEmpty();
    }

    @Test
    @DisplayName("Una cookie de estado retocada no vale: la firma no cuadra")
    void laCookieRetocadaNoVale() throws Exception {
        Ida ida = iniciar("google", "", null);
        PROVEEDOR.proximoToken(deGoogle(ida, sujeto(), persona("Ana").getEmail()));
        String cuerpo = ida.cookie().substring(0, ida.cookie().lastIndexOf('.'));
        String firma = ida.cookie().substring(ida.cookie().lastIndexOf('.') + 1);
        // El mismo contenido con otra letra, y la firma de siempre.
        String retocada = (cuerpo.charAt(0) == 'A' ? 'B' : 'A') + cuerpo.substring(1) + "." + firma;

        noHaEntrado(get("/auth/sso/google/vuelta?code=un-codigo&state=" + ida.state(), "nx_sso=" + retocada), "fallo");
        noHaEntrado(get("/auth/sso/google/vuelta?code=un-codigo&state=" + ida.state(), "nx_sso=" + cuerpo + "."), "fallo");
        assertThat(PROVEEDOR.canjes()).isEmpty();
    }

    @Test
    @DisplayName("Un ID token que no es para esta ida, esta aplicación o de este emisor no abre sesión")
    void elTokenTieneQueSerElDeEstaIda() throws Exception {
        User ana = persona("Ana");

        // El nonce de otra ida: un token robado de otro inicio de sesión.
        Ida ida = iniciar("google", "", null);
        Map<String, Object> claims = deGoogle(ida, sujeto(), ana.getEmail());
        claims.put("nonce", "el-de-otra-ida");
        PROVEEDOR.proximoToken(claims);
        noHaEntrado(volver("google", ida), "fallo");

        // Emitido para OTRA aplicación de Google.
        ida = iniciar("google", "", null);
        claims = deGoogle(ida, sujeto(), ana.getEmail());
        claims.put("aud", "la-aplicacion-de-otro");
        PROVEEDOR.proximoToken(claims);
        noHaEntrado(volver("google", ida), "fallo");

        // De otro emisor.
        ida = iniciar("google", "", null);
        claims = deGoogle(ida, sujeto(), ana.getEmail());
        claims.put("iss", "https://accounts.google.com.malo.example");
        PROVEEDOR.proximoToken(claims);
        noHaEntrado(volver("google", ida), "fallo");

        // Caducado.
        ida = iniciar("google", "", null);
        claims = deGoogle(ida, sujeto(), ana.getEmail());
        claims.put("exp", Date.from(Instant.now().minusSeconds(3600)));
        PROVEEDOR.proximoToken(claims);
        noHaEntrado(volver("google", ida), "fallo");

        // Sin sujeto.
        ida = iniciar("google", "", null);
        claims = deGoogle(ida, sujeto(), ana.getEmail());
        claims.remove("sub");
        PROVEEDOR.proximoToken(claims);
        noHaEntrado(volver("google", ida), "fallo");

        assertThat(identidadesDe(ana)).isZero();
    }

    @Test
    @DisplayName("Un ID token firmado con una clave que el proveedor no publica no vale")
    void laFirmaTieneQueSerDelProveedor() throws Exception {
        User ana = persona("Ana");
        Ida ida = iniciar("google", "", null);
        PROVEEDOR.proximoToken(deGoogle(ida, sujeto(), ana.getEmail()));
        PROVEEDOR.firmarElProximoConOtraClave();

        noHaEntrado(volver("google", ida), "fallo");
        assertThat(identidadesDe(ana)).isZero();
    }

    @Test
    @DisplayName("Si el proveedor rechaza el código, no se entra")
    void elProveedorRechazaElCodigo() throws Exception {
        Ida ida = iniciar("google", "", null);
        PROVEEDOR.proximoToken(deGoogle(ida, sujeto(), persona("Ana").getEmail()));
        PROVEEDOR.rechazarElProximoCanje();

        noHaEntrado(volver("google", ida), "fallo");
    }

    @Test
    @DisplayName("La cookie de estado vale para una vuelta: repetirla con el mismo código no vuelve a entrar")
    void laIdaSeGastaAlVolver() throws Exception {
        User ana = persona("Ana");
        Ida ida = iniciar("google", "", null);
        PROVEEDOR.proximoToken(deGoogle(ida, sujeto(), ana.getEmail()));
        HttpResponse<String> primera = volver("google", ida);
        assertThat(cookie(primera, "nx_refresh")).isNotBlank();

        // El servidor manda borrarla; un navegador que obedece ya no la tiene.
        assertThat(cookie(primera, "nx_sso")).isEmpty();
        assertThat(atributos(primera, "nx_sso")).contains("Max-Age=0");
    }

    // ------------------------------------------------------------------
    // Microsoft: a quién se le cree el correo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Microsoft, cuenta de una organización: sin xms_edov no se cree el correo; con él, sí")
    void microsoftDeOrganizacion() throws Exception {
        User ana = persona("Ana");

        Ida ida = iniciar("microsoft", "", null);
        PROVEEDOR.proximoToken(deMicrosoft(ida, sujeto(), ana.getEmail(), INQUILINO));
        noHaEntrado(volver("microsoft", ida), "correo-sin-verificar");
        assertThat(identidadesDe(ana)).isZero();

        ida = iniciar("microsoft", "", null);
        Map<String, Object> claims = deMicrosoft(ida, sujeto(), ana.getEmail(), INQUILINO);
        claims.put("xms_edov", true);
        PROVEEDOR.proximoToken(claims);
        HttpResponse<String> vuelta = volver("microsoft", ida);

        assertThat(destino(vuelta)).isEqualTo(WEB + "/");
        assertThat(cookie(vuelta, "nx_refresh")).isNotBlank();
        assertThat(PROVEEDOR.canjes().get(1)).containsEntry("client_id", ID_MICROSOFT);
    }

    @Test
    @DisplayName("Microsoft, cuenta personal: el correo lo verifica Microsoft y se cree")
    void microsoftPersonal() throws Exception {
        User ana = persona("Ana");
        Ida ida = iniciar("microsoft", "", null);
        PROVEEDOR.proximoToken(deMicrosoft(ida, sujeto(), ana.getEmail(), INQUILINO_PERSONAL));

        assertThat(cookie(volver("microsoft", ida), "nx_refresh")).isNotBlank();
    }

    @Test
    @DisplayName("Microsoft: el emisor tiene que ser el del inquilino del propio token, y el inquilino, un GUID")
    void microsoftEmisor() throws Exception {
        User ana = persona("Ana");

        Ida ida = iniciar("microsoft", "", null);
        Map<String, Object> claims = deMicrosoft(ida, sujeto(), ana.getEmail(), INQUILINO_PERSONAL);
        // Dice ser del inquilino personal, pero lo emite otro.
        claims.put("iss", "https://login.microsoftonline.com/" + INQUILINO + "/v2.0");
        PROVEEDOR.proximoToken(claims);
        noHaEntrado(volver("microsoft", ida), "fallo");

        ida = iniciar("microsoft", "", null);
        claims = deMicrosoft(ida, sujeto(), ana.getEmail(), INQUILINO_PERSONAL);
        claims.put("tid", "common/../otra-cosa");
        claims.put("iss", "https://login.microsoftonline.com/common/../otra-cosa/v2.0");
        PROVEEDOR.proximoToken(claims);
        noHaEntrado(volver("microsoft", ida), "fallo");

        // Un token de Google presentado por la puerta de Microsoft.
        ida = iniciar("microsoft", "", null);
        PROVEEDOR.proximoToken(deGoogle(ida, sujeto(), ana.getEmail()));
        noHaEntrado(volver("microsoft", ida), "fallo");

        assertThat(identidadesDe(ana)).isZero();
    }

    // ------------------------------------------------------------------
    // La app
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La app: vuelve con un código, lo canjea con su verificador y tiene la sesión; el código no vale dos veces")
    void laApp() throws Exception {
        User ana = persona("Ana");
        String verificador = "el-verificador-que-se-queda-la-app-0123456789";
        Ida ida = iniciar("google", "?cliente=app&reto=" + AppExchangeCodes.retoDe(verificador), null);
        PROVEEDOR.proximoToken(deGoogle(ida, sujeto(), ana.getEmail()));

        HttpResponse<String> vuelta = volver("google", ida);

        assertThat(destino(vuelta)).startsWith("nxtime://sso?codigo=");
        // A la app no se le ponen cookies: la sesión se la lleva canjeando.
        assertThat(cookie(vuelta, "nx_refresh")).isNull();
        String codigo = parametros(destino(vuelta)).get("codigo");

        HttpResponse<String> sesion = post("/auth/sso/canjear", Map.of("codigo", codigo, "verificador", verificador));
        assertThat(sesion.statusCode()).isEqualTo(200);
        JsonNode cuerpo = json.readTree(sesion.body());
        assertThat(cuerpo.get("token").asText()).isNotBlank();
        // En el cuerpo, como en el login de la app: no hay cookie.
        assertThat(cuerpo.get("refreshToken").asText()).isNotBlank();
        assertThat(cuerpo.get("nombre").asText()).isEqualTo("Ana");

        assertThat(post("/auth/sso/canjear", Map.of("codigo", codigo, "verificador", verificador)).statusCode())
                .as("el código ya se ha usado").isEqualTo(400);
    }

    @Test
    @DisplayName("La app: quien se queda con el código pero no tiene el verificador no canjea nada, y lo gasta")
    void laAppSinElVerificador() throws Exception {
        User ana = persona("Ana");
        String verificador = "el-verificador-que-se-queda-la-app-0123456789";
        Ida ida = iniciar("google", "?cliente=app&reto=" + AppExchangeCodes.retoDe(verificador), null);
        PROVEEDOR.proximoToken(deGoogle(ida, sujeto(), ana.getEmail()));
        String codigo = parametros(destino(volver("google", ida))).get("codigo");

        assertThat(post("/auth/sso/canjear", Map.of("codigo", codigo, "verificador", "otro-verificador")).statusCode())
                .isEqualTo(400);
        // Ni siquiera la app legítima, después: un intento fallido lo quema.
        assertThat(post("/auth/sso/canjear", Map.of("codigo", codigo, "verificador", verificador)).statusCode())
                .isEqualTo(400);
        assertThat(post("/auth/sso/canjear", Map.of("codigo", "inventado", "verificador", verificador)).statusCode())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("La app: sin un reto válido no empieza, y los errores vuelven a la app")
    void laAppErrores() throws Exception {
        assertThat(destino(get("/auth/sso/google/iniciar?cliente=app", null))).isEqualTo("nxtime://sso?error=fallo");
        assertThat(destino(get("/auth/sso/google/iniciar?cliente=app&reto=corto", null)))
                .isEqualTo("nxtime://sso?error=fallo");

        String verificador = "el-verificador-que-se-queda-la-app-0123456789";
        Ida ida = iniciar("google", "?cliente=app&reto=" + AppExchangeCodes.retoDe(verificador), null);
        PROVEEDOR.proximoToken(deGoogle(ida, sujeto(), "nadie-" + System.nanoTime() + "@sso.test"));

        assertThat(destino(volver("google", ida))).isEqualTo("nxtime://sso?error=sin-cuenta");
    }

    // ------------------------------------------------------------------
    // Vincular desde la sesión abierta
    // ------------------------------------------------------------------

    /** Entra con contraseña desde la web y devuelve {refresh, csrf, token}. */
    private String[] entrarConContrasena(User usuario) throws Exception {
        HttpResponse<String> login = post("/auth/login",
                Map.of("email", usuario.getEmail(), "contrasena", "password123", "origen", "WEB"));
        assertThat(login.statusCode()).isEqualTo(200);
        return new String[] {
                cookie(login, "nx_refresh"), cookie(login, "nx_csrf"), json.readTree(login.body()).get("token").asText()};
    }

    private HttpResponse<String> conToken(String metodo, String ruta, String token) throws Exception {
        return navegador.send(peticion(ruta).header("Authorization", "Bearer " + token)
                .method(metodo, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Vincular: con la sesión abierta se añade una cuenta aunque su correo no esté garantizado, se lista y se quita")
    void vincularYDesvincular() throws Exception {
        User ana = persona("Ana");
        String[] sesion = entrarConContrasena(ana);
        String sujeto = sujeto();

        Ida ida = iniciar("microsoft", "?vincular=1", "nx_refresh=" + sesion[0]);
        // Una cuenta de trabajo con OTRO correo y sin xms_edov: por el botón de
        // entrar no habría pasado. Aquí sí: controla las dos cuentas.
        PROVEEDOR.proximoToken(deMicrosoft(ida, sujeto, "ana.trabajo@empresa.example", INQUILINO));
        HttpResponse<String> vuelta = volver("microsoft", ida);

        assertThat(destino(vuelta)).isEqualTo(WEB + "/ajustes?sso=vinculada");
        // Vincular no abre otra sesión.
        assertThat(cookie(vuelta, "nx_refresh")).isNull();

        JsonNode lista = json.readTree(conToken("GET", "/api/v1/perfil/identidades", sesion[2]).body());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("proveedor").asText()).isEqualTo("microsoft");
        assertThat(lista.get(0).get("nombre").asText()).isEqualTo("Microsoft");
        assertThat(lista.get(0).get("correo").asText()).isEqualTo("ana.trabajo@empresa.example");

        // Y ya entra con ella, por el sujeto.
        Ida entrada = iniciar("microsoft", "", null);
        PROVEEDOR.proximoToken(deMicrosoft(entrada, sujeto, "ana.trabajo@empresa.example", INQUILINO));
        assertThat(cookie(volver("microsoft", entrada), "nx_refresh")).isNotBlank();

        assertThat(conToken("DELETE", "/api/v1/perfil/identidades/microsoft", sesion[2]).statusCode()).isEqualTo(204);
        assertThat(identidadesDe(ana)).isZero();
        assertThat(conToken("DELETE", "/api/v1/perfil/identidades/microsoft", sesion[2]).statusCode()).isEqualTo(404);

        // Desvinculada, deja de entrar.
        entrada = iniciar("microsoft", "", null);
        PROVEEDOR.proximoToken(deMicrosoft(entrada, sujeto, "ana.trabajo@empresa.example", INQUILINO));
        assertThat(destino(volver("microsoft", entrada))).isEqualTo(WEB + "/?sso=correo-sin-verificar");
    }

    @Test
    @DisplayName("Vincular: sin sesión no empieza, y una cuenta que ya es de otra persona no se puede vincular")
    void vincularLoQueNoSePuede() throws Exception {
        assertThat(destino(get("/auth/sso/google/iniciar?vincular=1", null))).isEqualTo(WEB + "/ajustes?sso=sin-sesion");
        assertThat(destino(get("/auth/sso/google/iniciar?vincular=1", "nx_refresh=inventado")))
                .isEqualTo(WEB + "/ajustes?sso=sin-sesion");

        User ana = persona("Ana");
        String deAna = sujeto();
        assertThat(cookie(entrarConGoogle(deAna, ana.getEmail()), "nx_refresh")).isNotBlank();

        User javi = persona("Javi");
        String[] sesion = entrarConContrasena(javi);
        Ida ida = iniciar("google", "?vincular=1", "nx_refresh=" + sesion[0]);
        PROVEEDOR.proximoToken(deGoogle(ida, deAna, ana.getEmail()));

        assertThat(destino(volver("google", ida))).isEqualTo(WEB + "/ajustes?sso=ya-vinculada");
        assertThat(identidadesDe(javi)).isZero();
        assertThat(identidadesDe(ana)).isEqualTo(1);
    }

    @Test
    @DisplayName("Las cuentas vinculadas piden sesión")
    void lasIdentidadesPidenSesion() throws Exception {
        assertThat(get("/api/v1/perfil/identidades", null).statusCode()).isEqualTo(401);
    }

    // ------------------------------------------------------------------
    // Vincular desde la app
    // ------------------------------------------------------------------

    private static final String VERIFICADOR = "el-verificador-que-se-queda-la-app-0123456789";

    /** Entra con contraseña como la app (sin cookies) y devuelve su access token. */
    private String entrarDesdeLaApp(User usuario) throws Exception {
        HttpResponse<String> login =
                post("/auth/login", Map.of("email", usuario.getEmail(), "contrasena", "password123"));
        assertThat(login.statusCode()).isEqualTo(200);
        return json.readTree(login.body()).get("token").asText();
    }

    /** La app empieza a vincular y el navegador vuelve: el código de vínculo que trae. */
    private String codigoDeVinculo(String proveedor, Map<String, Object> token, Ida ida) throws Exception {
        PROVEEDOR.proximoToken(token);
        HttpResponse<String> vuelta = volver(proveedor, ida);
        assertThat(destino(vuelta)).startsWith("nxtime://sso?vinculo=");
        // Ni sesión ni nada vinculado todavía: eso lo hace la app al confirmar.
        assertThat(cookie(vuelta, "nx_refresh")).isNull();
        return parametros(destino(vuelta)).get("vinculo");
    }

    private Ida iniciarVinculoDesdeLaApp(String proveedor) throws Exception {
        return iniciar(proveedor, "?cliente=app&vincular=1&reto=" + AppExchangeCodes.retoDe(VERIFICADOR), null);
    }

    private HttpResponse<String> confirmarVinculo(String token, String codigo, String verificador) throws Exception {
        return post("/api/v1/perfil/identidades", Map.of("codigo", codigo, "verificador", verificador),
                "Authorization", "Bearer " + token);
    }

    @Test
    @DisplayName("La app vincula: el navegador vuelve con un código, ella lo confirma con su sesión y ya entra con esa cuenta")
    void laAppVincula() throws Exception {
        User ana = persona("Ana");
        String token = entrarDesdeLaApp(ana);
        String sujeto = sujeto();

        Ida ida = iniciarVinculoDesdeLaApp("microsoft");
        // Como desde la web: una cuenta de trabajo con otro correo y sin xms_edov.
        String codigo = codigoDeVinculo(
                "microsoft", deMicrosoft(ida, sujeto, "ana.trabajo@empresa.example", INQUILINO), ida);
        assertThat(identidadesDe(ana)).as("la vuelta del navegador no vincula nada").isZero();

        HttpResponse<String> confirmado = confirmarVinculo(token, codigo, VERIFICADOR);

        assertThat(confirmado.statusCode()).isEqualTo(200);
        JsonNode lista = json.readTree(confirmado.body());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("proveedor").asText()).isEqualTo("microsoft");
        assertThat(lista.get(0).get("correo").asText()).isEqualTo("ana.trabajo@empresa.example");
        assertThat(identidadesDe(ana)).isEqualTo(1);

        // El código no vale dos veces.
        assertThat(confirmarVinculo(token, codigo, VERIFICADOR).statusCode()).isEqualTo(400);

        // Y ya entra con ella desde la app, por el sujeto.
        Ida entrada = iniciar("microsoft", "?cliente=app&reto=" + AppExchangeCodes.retoDe(VERIFICADOR), null);
        PROVEEDOR.proximoToken(deMicrosoft(entrada, sujeto, "ana.trabajo@empresa.example", INQUILINO));
        assertThat(destino(volver("microsoft", entrada))).startsWith("nxtime://sso?codigo=");
    }

    /**
     * Lo que protege el diseño en dos pasos: el código que vuelve por la URL
     * no dice a quién vincular, y solo lo puede usar quien empezó.
     */
    @Test
    @DisplayName("La app vincula: sin sesión, sin el verificador o con un código de entrar no se vincula nada")
    void laAppVinculaLoQueNoSePuede() throws Exception {
        User ana = persona("Ana");
        String token = entrarDesdeLaApp(ana);

        // Sin sesión: 401, y el código ni se mira.
        Ida ida = iniciarVinculoDesdeLaApp("google");
        String codigo = codigoDeVinculo("google", deGoogle(ida, sujeto(), "ana.personal@gmail.test"), ida);
        assertThat(post("/api/v1/perfil/identidades", Map.of("codigo", codigo, "verificador", VERIFICADOR))
                .statusCode()).isEqualTo(401);

        // Sin el verificador de esa ida: 400, y el código se quema.
        assertThat(confirmarVinculo(token, codigo, "otro-verificador").statusCode()).isEqualTo(400);
        assertThat(confirmarVinculo(token, codigo, VERIFICADOR).statusCode()).isEqualTo(400);

        // Un código de ENTRAR no sirve para vincular...
        Ida entrada = iniciar("google", "?cliente=app&reto=" + AppExchangeCodes.retoDe(VERIFICADOR), null);
        PROVEEDOR.proximoToken(deGoogle(entrada, sujeto(), ana.getEmail()));
        String deEntrar = parametros(destino(volver("google", entrada))).get("codigo");
        assertThat(confirmarVinculo(token, deEntrar, VERIFICADOR).statusCode()).isEqualTo(400);

        // ...ni uno de VINCULAR abre una sesión.
        ida = iniciarVinculoDesdeLaApp("microsoft");
        String deVincular = codigoDeVinculo(
                "microsoft", deMicrosoft(ida, sujeto(), "ana.trabajo@empresa.example", INQUILINO), ida);
        assertThat(post("/auth/sso/canjear", Map.of("codigo", deVincular, "verificador", VERIFICADOR)).statusCode())
                .isEqualTo(400);

        // Sin reto no empieza, y el error vuelve a la app.
        assertThat(destino(get("/auth/sso/google/iniciar?cliente=app&vincular=1", null)))
                .isEqualTo("nxtime://sso?error=fallo");

        assertThat(identidadesDe(ana)).isEqualTo(1); // solo la de haber entrado con Google arriba
    }

    @Test
    @DisplayName("La app vincula: una cuenta que ya es de otra persona da 409 y no cambia de dueño")
    void laAppVinculaUnaCuentaAjena() throws Exception {
        User ana = persona("Ana");
        String deAna = sujeto();
        assertThat(cookie(entrarConGoogle(deAna, ana.getEmail()), "nx_refresh")).isNotBlank();

        User javi = persona("Javi");
        String token = entrarDesdeLaApp(javi);
        Ida ida = iniciarVinculoDesdeLaApp("google");
        String codigo = codigoDeVinculo("google", deGoogle(ida, deAna, ana.getEmail()), ida);

        HttpResponse<String> respuesta = confirmarVinculo(token, codigo, VERIFICADOR);

        assertThat(respuesta.statusCode()).isEqualTo(409);
        assertThat(json.readTree(respuesta.body()).get("detail").asText()).contains("otra persona");
        assertThat(identidadesDe(javi)).isZero();
        assertThat(identidadesDe(ana)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Borrado de datos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El borrado de datos se lleva las cuentas vinculadas, y solo las de esa persona")
    void elBorradoSeLasLleva() throws Exception {
        User ana = persona("Ana");
        User javi = persona("Javi");
        entrarConGoogle(sujeto(), ana.getEmail());
        entrarConGoogle(sujeto(), javi.getEmail());

        Map<String, Integer> borrado = eraser.purgar(ana.getId());

        assertThat(borrado).containsEntry("identidadesExternas", 1);
        assertThat(identidadesDe(ana)).isZero();
        assertThat(identidadesDe(javi)).isEqualTo(1);
    }

    @Test
    @DisplayName("Dos idas a la vez no se pisan los datos: cada vuelta lleva lo suyo")
    void dosIdasDistintas() throws Exception {
        List<Ida> idas = List.of(iniciar("google", "", null), iniciar("google", "", null));

        assertThat(idas.get(0).state()).isNotEqualTo(idas.get(1).state());
        assertThat(idas.get(0).nonce()).isNotEqualTo(idas.get(1).nonce());
        assertThat(idas.get(0).cookie()).isNotEqualTo(idas.get(1).cookie());
    }
}
