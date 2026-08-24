package com.nxtime.nxtime.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Value;
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

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de CONTRATO (Fase 0 del plan de profesionalización).
 *
 * Objetivo: fijar en un test ejecutable la forma real de los 16 endpoints
 * HTTP que hoy consume la app Android, TAL COMO SE COMPORTAN AHORA MISMO
 * -- incluyendo sus defectos conocidos (ver auditoría del plan).
 *
 * Este test es la red de seguridad para dos cosas muy distintas:
 *
 *   - Fase 1 (migración Kotlin -> Java): el criterio de aceptación es que
 *     este fichero pase SIN MODIFICARLO. Por eso está escrito en Java,
 *     usando JSON crudo (Map/JsonNode) en vez de las clases Kotlin del
 *     proyecto -- así no depende de nada que la migración vaya a borrar.
 *
 *   - Fase 2 (manejo de errores, DTOs, etc.): ahí el contrato SÍ cambia a
 *     propósito. Los tests marcados "BUG ACTUAL" documentan el
 *     comportamiento defectuoso de hoy (ver plan, sección "Defectos
 *     críticos") y hay que actualizarlos deliberadamente en esa fase.
 *
 * Cada base de datos SQLite de test es nueva y aislada (ver
 * datasourceProperties): se puede ejecutar el conjunto de tests
 * repetidamente sin colisiones de datos entre ejecuciones.
 *
 * Los tests están ordenados porque construyen un flujo de negocio
 * encadenado (registrar empresa -> crear empleado -> fichar -> pedir
 * ausencia -> aprobarla...), igual que lo haría la app real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiContractTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        String dbFile = "build/test-dbs/contract-" + System.nanoTime() + ".db";
        new File(dbFile).getParentFile().mkdirs();
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + dbFile);
    }

    @Value("${local.server.port}")
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate();
    private final ObjectMapper json = new ObjectMapper();

    // Estado compartido entre tests ordenados (construyen un flujo real).
    private static final String EMPRESA = "Contract Test SL";
    private static final String EMAIL_GESTOR = "gestor.contract@nxtime.test";
    private static final String EMAIL_EMPLEADO = "empleado.contract@nxtime.test";
    private static final String EMAIL_GESTOR2 = "gestor2.contract@nxtime.test";
    private String gestorToken;
    private String empleadoToken;
    private long empleadoId;
    private long registroActivoId;
    private long peticionAusenciaId;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String toJson(Map<String, Object> body) throws Exception {
        return json.writeValueAsString(body);
    }

    private JsonNode bodyOf(ResponseEntity<String> response) throws Exception {
        return json.readTree(response.getBody());
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    // ------------------------------------------------------------------
    // 1. AUTENTICACIÓN
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void registrarGestor_datosValidos_devuelve200ConTokenNombreYRol() throws Exception {
        Map<String, Object> peticion = mapOf(
                "nombreEmpresa", EMPRESA,
                "nombreGestor", "Gestor Contract",
                "email", EMAIL_GESTOR,
                "password", "password123"
        );

        ResponseEntity<String> response = rest.postForEntity(
                url("/auth/register-manager"),
                new HttpEntity<>(toJson(peticion), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("nombre").asText()).isEqualTo("Gestor Contract");
        assertThat(body.get("rol").asText()).isEqualTo("GESTOR");

        gestorToken = body.get("token").asText();
    }

    @Test
    @Order(2)
    void registrarGestor_empresaDuplicada_devuelve403EnVezDe409_bugActual() throws Exception {
        // BUG ACTUAL, más grave de lo que parece (verificado empíricamente,
        // no es una suposición): el servicio SÍ lanza correctamente
        // ResponseStatusException(HttpStatus.CONFLICT, ...), y Spring MVC
        // SÍ lo resuelve a 409 (se ve en el log: "Resolved
        // [...ResponseStatusException: 409 CONFLICT...]"). Pero
        // ResponseStatusException usa response.sendError(), que dispara un
        // dispatch interno a "/error". Como Spring Security aplica su
        // cadena de filtros también a los dispatch ERROR (comportamiento
        // por defecto) y "/error" no coincide con "/auth/**" ni
        // "/api/v1/**", cae en el `.anyRequest().denyAll()` de
        // ConfiguracionSeguridad -> el cliente recibe 403, no 409.
        // Es decir: HOY, absolutamente NINGÚN error de la API llega tal
        // cual al cliente; todos acaban como 403. Se corrige en la Fase 2
        // (junto con el resto del manejo de errores) o antes, en la Fase 0
        // si se decide que es lo bastante grave como para no esperar.
        Map<String, Object> peticion = mapOf(
                "nombreEmpresa", EMPRESA, // misma empresa que en el test anterior
                "nombreGestor", "Otro Gestor",
                "email", "otro.gestor@nxtime.test",
                "password", "password123"
        );

        ResponseEntity<String> response = rest.postForEntity(
                url("/auth/register-manager"),
                new HttpEntity<>(toJson(peticion), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(3)
    void login_credencialesDeGestorValidas_devuelve200ConToken() throws Exception {
        Map<String, Object> peticion = mapOf(
                "email", EMAIL_GESTOR,
                "contrasena", "password123"
        );

        ResponseEntity<String> response = rest.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(toJson(peticion), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("rol").asText()).isEqualTo("GESTOR");

        gestorToken = body.get("token").asText();
    }

    @Test
    @Order(4)
    void login_contrasenaIncorrecta_esUnErrorDeServidor_bugActual() {
        // BUG ACTUAL (ver plan, defecto #2 "Cero manejo de errores"): al no
        // haber un @ExceptionHandler para BadCredentialsException, una
        // contraseña incorrecta en /auth/login no produce un 401 sino un
        // error no controlado. Este test documenta el estado real de hoy;
        // en la Fase 2 debe pasar a esperar 401 con ProblemDetail.
        Map<String, Object> peticion = Map.of(
                "email", EMAIL_GESTOR,
                "contrasena", "contrasena-incorrecta"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/auth/login"),
                HttpMethod.POST,
                new HttpEntity<>(peticion, jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode().is4xxClientError()
                || response.getStatusCode().is5xxServerError()).isTrue();
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(5)
    void endpointProtegido_sinToken_esRechazado() {
        ResponseEntity<String> response = rest.getForEntity(
                url("/api/v1/fichaje/activo"), String.class);

        // Sin @AuthenticationEntryPoint personalizado, Spring Security 6
        // devuelve 403 (no 401) para una petición no autenticada contra una
        // ruta que exige authenticated(). Documentado como comportamiento
        // actual; revisar en la Fase 4 (seguridad reforzada).
        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    // ------------------------------------------------------------------
    // 2. GESTIÓN DE EMPLEADOS (rol GESTOR)
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    void gestorCreaEmpleado_devuelve200() throws Exception {
        Map<String, Object> peticion = mapOf(
                "nombre", "Empleado Contract",
                "email", EMAIL_EMPLEADO,
                "contrasena", "password123"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/empleados"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(11)
    void gestorCreaOtroGestor_devuelve200() throws Exception {
        Map<String, Object> peticion = mapOf(
                "nombre", "Gestor Contract 2",
                "email", EMAIL_GESTOR2,
                "contrasena", "password123"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/gestores"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(12)
    void loginEmpleado_devuelve200ConRolEmpleado() throws Exception {
        Map<String, Object> peticion = mapOf(
                "email", EMAIL_EMPLEADO,
                "contrasena", "password123"
        );

        ResponseEntity<String> response = rest.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(toJson(peticion), jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("rol").asText()).isEqualTo("EMPLEADO");

        empleadoToken = body.get("token").asText();
    }

    @Test
    @Order(13)
    void gestorListaSusEmpleados_incluyeAlRecienCreado() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/mis-empleados"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.isArray()).isTrue();

        JsonNode empleado = null;
        for (JsonNode n : body) {
            if (EMAIL_EMPLEADO.equals(n.get("email").asText())) {
                empleado = n;
            }
        }
        assertThat(empleado).as("el empleado recién creado debe aparecer en la lista").isNotNull();
        empleadoId = empleado.get("id").asLong();
    }

    @Test
    @Order(14)
    void empleadoNoPuedeAccederAEndpointDeGestor_devuelve403() {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/mis-empleados"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------
    // 3. FICHAJE
    // ------------------------------------------------------------------

    @Test
    @Order(20)
    void ficharInicio_devuelve200_yHOY_filtraElHashDeLaContrasena_bugActual() throws Exception {
        Map<String, Object> peticion = mapOf("tipo", "INICIO");

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("horaEntrada").asText()).isNotBlank();
        registroActivoId = body.get("id").asLong();

        // BUG ACTUAL (ver plan, defecto #1 "Fuga del hash BCrypt por la
        // API"): el controlador devuelve la entidad JPA completa, que
        // arrastra al usuario y su contraseña cifrada. Esta aserción
        // documenta la fuga; en la Fase 2 el endpoint debe pasar a un DTO
        // y este bloque debe invertirse (comprobar que NO aparece).
        assertThat(body.has("usuario")).as("hoy la entidad Usuario viaja anidada").isTrue();
        assertThat(body.get("usuario").has("contrasena"))
                .as("BUG: el hash de la contraseña se filtra por la API")
                .isTrue();
    }

    @Test
    @Order(21)
    void ficharActivo_devuelveElRegistroAbierto() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/activo"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("id").asLong()).isEqualTo(registroActivoId);
    }

    @Test
    @Order(22)
    void ficharInicioDosVeces_devuelve403EnVezDe409_bugActual() throws Exception {
        // BUG ACTUAL: el IllegalStateException de "Ya hay una jornada
        // activa" no está mapeado por ningún @ExceptionHandler, así que
        // Spring Boot lo traduce internamente a un dispatch a "/error" ->
        // que vuelve a pasar por el filtro de seguridad -> denyAll() -> el
        // cliente recibe 403 (mismo mecanismo que en
        // registrarGestor_empresaDuplicada_devuelve403EnVezDe409_bugActual,
        // verificado empíricamente). En la Fase 2 debe pasar a un 409 real
        // con ProblemDetail.
        Map<String, Object> peticion = mapOf("tipo", "INICIO");

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(23)
    void ficharPausaInicioYFin_devuelven200() throws Exception {
        ResponseEntity<String> pausaInicio = rest.exchange(
                url("/api/v1/fichaje"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("tipo", "PAUSA_INICIO")), authHeaders(empleadoToken)),
                String.class
        );
        assertThat(pausaInicio.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(pausaInicio).get("enPausa").asBoolean()).isTrue();

        ResponseEntity<String> pausaFin = rest.exchange(
                url("/api/v1/fichaje"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("tipo", "PAUSA_FIN")), authHeaders(empleadoToken)),
                String.class
        );
        assertThat(pausaFin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(pausaFin).get("enPausa").asBoolean()).isFalse();
    }

    @Test
    @Order(24)
    void ficharFin_cierraLaJornada_devuelve200() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(mapOf("tipo", "FIN")), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("horaSalida").isNull()).isFalse();
    }

    @Test
    @Order(25)
    void ficharActivo_sinJornadaAbierta_devuelve204() {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/activo"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @Order(26)
    void historialDelEmpleado_incluyeElFichajeCerrado() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/historial"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(27)
    void historialDelEquipo_paraElGestor_devuelveFechaYHoraComoTextoPreformateado() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/gestor/historial"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isGreaterThanOrEqualTo(1);

        JsonNode primero = body.get(0);
        // Formato actual: "HH:mm:ss" / "yyyy-MM-dd" como String plano, no
        // ISO-8601 tipado (ver plan, defectos de diseño). Se corrige en
        // Fase 2.
        assertThat(primero.get("horaEntrada").asText()).matches("\\d{2}:\\d{2}:\\d{2}");
        assertThat(primero.get("fecha").asText()).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(primero.get("usuario").get("nombre").asText()).isNotBlank();
    }

    // ------------------------------------------------------------------
    // 4. AUSENCIAS
    // ------------------------------------------------------------------

    @Test
    @Order(30)
    void empleadoSolicitaAusencia_devuelve200ConEstadoPendiente() throws Exception {
        Map<String, Object> peticion = mapOf(
                "fechaInicio", "2027-01-10",
                "fechaFin", "2027-01-12",
                "tipo", "VACACIONES",
                "motivo", "Test de contrato"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(body.get("tipo").asText()).isEqualTo("VACACIONES");
        assertThat(body.get("fechaInicio").asText()).isEqualTo("2027-01-10");

        peticionAusenciaId = body.get("id").asLong();
    }

    @Test
    @Order(31)
    void empleadoVeSusPropiasPeticiones() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias/mis-peticiones"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        assertThat(body.size()).isEqualTo(1);
    }

    @Test
    @Order(32)
    void gestorVeLaPeticionPendienteDeSuEquipo() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias/gestor/pendientes"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        boolean encontrada = false;
        for (JsonNode n : body) {
            if (n.get("id").asLong() == peticionAusenciaId) {
                encontrada = true;
            }
        }
        assertThat(encontrada).isTrue();
    }

    @Test
    @Order(33)
    void gestorApruebaLaPeticion_devuelve200ConEstadoAprobada() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias/gestor/aprobar/" + peticionAusenciaId),
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(response).get("estado").asText()).isEqualTo("APROBADA");
    }

    @Test
    @Order(34)
    void aprobarUnaPeticionYaResuelta_devuelve403EnVezDe409_bugActual() throws Exception {
        // BUG ACTUAL: IllegalStateException("Solo se puede modificar una
        // petición PENDIENTE.") tampoco está mapeado -> mismo mecanismo de
        // /error + denyAll() -> 403 en vez del 409 que correspondería.
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/ausencias/gestor/aprobar/" + peticionAusenciaId),
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(35)
    void gestorVeElHistorialDeAusenciasResueltas() throws Exception {
        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/gestor/ausencias-historial"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = bodyOf(response);
        boolean encontrada = false;
        for (JsonNode n : body) {
            if (n.get("id").asLong() == peticionAusenciaId && "APROBADA".equals(n.get("estado").asText())) {
                encontrada = true;
            }
        }
        assertThat(encontrada).isTrue();
    }

    @Test
    @Order(36)
    void unaSegundaSolicitudPuedeSerRechazada() throws Exception {
        Map<String, Object> nuevaPeticion = mapOf(
                "fechaInicio", "2027-02-01",
                "fechaFin", "2027-02-02",
                "tipo", "MEDICO",
                "motivo", null
        );
        ResponseEntity<String> creada = rest.exchange(
                url("/api/v1/ausencias"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(nuevaPeticion), authHeaders(empleadoToken)),
                String.class
        );
        long id = bodyOf(creada).get("id").asLong();

        ResponseEntity<String> rechazo = rest.exchange(
                url("/api/v1/ausencias/gestor/rechazar/" + id),
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(gestorToken)),
                String.class
        );

        assertThat(rechazo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bodyOf(rechazo).get("estado").asText()).isEqualTo("RECHAZADA");
    }

    // ------------------------------------------------------------------
    // 5. PERFIL DE USUARIO
    // ------------------------------------------------------------------

    @Test
    @Order(40)
    void cambiarContrasena_conContrasenaAntiguaIncorrecta_devuelve403EnVezDe400_bugActual() throws Exception {
        // BUG ACTUAL: mismo mecanismo que los anteriores. El servicio lanza
        // ResponseStatusException(BAD_REQUEST, ...) correctamente, pero el
        // cliente recibe 403 por el dispatch a "/error" + denyAll().
        Map<String, Object> peticion = mapOf(
                "contrasenaAntigua", "no-es-la-contrasena",
                "contrasenaNueva", "nuevaPassword123"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/usuario/cambiar-contrasena"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(empleadoToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(41)
    void cambiarContrasena_datosCorrectos_devuelve200_yPermiteLoginConLaNueva() throws Exception {
        Map<String, Object> peticion = mapOf(
                "contrasenaAntigua", "password123",
                "contrasenaNueva", "nuevaPassword123"
        );

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/usuario/cambiar-contrasena"),
                HttpMethod.POST,
                new HttpEntity<>(toJson(peticion), authHeaders(empleadoToken)),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> login = mapOf(
                "email", EMAIL_EMPLEADO,
                "contrasena", "nuevaPassword123"
        );
        ResponseEntity<String> loginResponse = rest.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(toJson(login), jsonHeaders()),
                String.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------
    // 6. TOKEN INVÁLIDO
    // ------------------------------------------------------------------

    @Test
    @Order(50)
    void tokenMalformado_devuelve403EnVezDe401_bugActual() {
        // BUG ACTUAL (ver plan, defecto #3): FiltroAutenticacionJwt no
        // envuelve en try/catch la lectura del token, así que un token
        // corrupto o caducado revienta el filtro con una excepción no
        // controlada. Aquí la excepción salta ANTES de que el filtro llame
        // a filterChain.doFilter(), así que nunca pasa por
        // ExceptionTranslationFilter -- pero el resultado final es el mismo
        // 403 (vía el dispatch a "/error" + denyAll(), verificado
        // empíricamente), en vez del 401 que debería devolver. Se corrige
        // en la Fase 4.
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth("esto-no-es-un-jwt-valido");

        ResponseEntity<String> response = rest.exchange(
                url("/api/v1/fichaje/activo"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
