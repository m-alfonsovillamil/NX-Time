package com.nxtime.nxtime.scheduled;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Test de INTEGRACIÓN del cierre automático de jornadas olvidadas
 * (Fase 9), contra PostgreSQL real.
 *
 * Existe aparte de {@link IncompleteTimeEntrySchedulerTest} (unitario,
 * con mocks) porque lo que de verdad hay que demostrar aquí solo existe
 * en la base de datos, no en el código Java:
 *
 *  1. Que el empleado PUEDE volver a fichar después del cierre. El bug
 *     que esto arregla no era cosmético: el índice parcial único
 *     uq_registros_jornada_abierta (Fase 3) impide dos jornadas
 *     abiertas a la vez, así que una jornada sin fichaje de salida
 *     bloqueaba TODOS los fichajes futuros de ese empleado. Con mocks
 *     ese índice no existe y el bug no se puede reproducir.
 *
 *  2. Que la auditoría acepta una fila con modificado_por_id NULL (la
 *     acción automática del sistema) -- esa columna era NOT NULL hasta
 *     V4__business_rules.sql, y el encadenamiento de hashes tiene que
 *     seguir funcionando sin autor humano.
 *
 * Requisito: `docker compose up -d postgres` (ver ApiContractTest).
 */
@SpringBootTest
class IncompleteTimeEntrySchedulerIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "scheduler_it_" + System.nanoTime();
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
    private IncompleteTimeEntryScheduler scheduler;
    @Autowired
    private TimeEntryService timeEntryService;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private TimeEntryAuditRepository timeEntryAuditRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    private final ObjectMapper json = new ObjectMapper();

    private User crearEmpleado(String email) {
        Company empresa = companyRepository.save(Company.builder().nombre("Empresa " + email).build());
        return userRepository.save(User.builder()
                .email(email).nombre("Empleado").contrasena("hash")
                .rol(Role.EMPLEADO).empresa(empresa).build());
    }

    @Test
    @DisplayName("Una jornada olvidada bloquea los fichajes siguientes, y el cierre automático los desbloquea")
    void cierreAutomatico_desbloqueaLosFichajesSiguientes() {
        User empleado = crearEmpleado("olvidadiza@nxtime.test");

        // Jornada abierta hace 30 horas: el empleado se fue sin fichar
        // la salida.
        timeEntryRepository.save(TimeEntry.builder()
                .usuario(empleado)
                .empresa(empleado.getEmpresa())
                .horaEntrada(Instant.now().minus(30, ChronoUnit.HOURS))
                .build());

        // ANTES del cierre: intentar fichar de nuevo falla. Este es el
        // bug -- sin el proceso nocturno, se quedaría así para siempre.
        assertThat(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).isPresent();

        scheduler.cerrarJornadasOlvidadas();

        // DESPUÉS: ya no hay jornada abierta, así que el índice parcial
        // único deja pasar un fichaje nuevo.
        assertThat(timeEntryRepository.findByUsuarioAndHoraSalidaIsNull(empleado)).isEmpty();

        TimeEntry nueva = timeEntryService.registerTimeEntry(
                empleado.getEmail(), new TimeEntryRequest(TimeEntryAction.INICIO));
        assertThat(nueva.getId()).isPositive();
        assertThat(nueva.getHoraSalida()).isNull();
    }

    @Test
    @DisplayName("La jornada cerrada por el sistema queda marcada como incompleta y con hora de salida acotada")
    void cierreAutomatico_marcaLaJornadaYAcotaLaHoraDeSalida() {
        User empleado = crearEmpleado("acotada@nxtime.test");
        Instant horaEntrada = Instant.now().minus(50, ChronoUnit.HOURS);
        TimeEntry olvidada = timeEntryRepository.save(TimeEntry.builder()
                .usuario(empleado).empresa(empleado.getEmpresa()).horaEntrada(horaEntrada).build());

        scheduler.cerrarJornadasOlvidadas();

        TimeEntry cerrada = timeEntryRepository.findById(olvidada.getId()).orElseThrow();
        assertThat(cerrada.isJornadaIncompleta()).isTrue();
        assertThat(cerrada.getHoraSalida()).isNotNull();
        // 16 h imputadas, no las 50 reales desde que se abrió.
        assertThat(ChronoUnit.HOURS.between(cerrada.getHoraEntrada(), cerrada.getHoraSalida())).isEqualTo(16);
    }

    @Test
    @DisplayName("El cierre automático escribe en auditoría con modificado_por_id NULL y encadena el hash")
    void cierreAutomatico_escribeAuditoriaSinAutorHumano() throws Exception {
        User empleado = crearEmpleado("auditada@nxtime.test");
        TimeEntry olvidada = timeEntryRepository.save(TimeEntry.builder()
                .usuario(empleado).empresa(empleado.getEmpresa())
                .horaEntrada(Instant.now().minus(30, ChronoUnit.HOURS)).build());

        scheduler.cerrarJornadasOlvidadas();

        List<TimeEntryAudit> trail =
                timeEntryAuditRepository.findByRegistro_IdOrderByFechaHoraAsc(olvidada.getId());
        assertThat(trail).hasSize(1);

        TimeEntryAudit fila = trail.get(0);
        assertThat(fila.getAccion()).isEqualTo(AuditAction.MODIFICACION);
        // La columna era NOT NULL hasta V4: si la migración no la
        // hubiera relajado, el INSERT de arriba habría reventado.
        assertThat(fila.getModificadoPor()).isNull();
        assertThat(fila.getUsuario().getId()).isEqualTo(empleado.getId());
        assertThat(fila.getMotivo()).contains("Cierre automático");
        // El hash se calcula igual sin autor humano.
        assertThat(fila.getHash()).isNotBlank().hasSize(64);

        // Las instantáneas se comparan PARSEANDO el JSON, no buscando
        // subcadenas: la columna es "jsonb", y jsonb no guarda el texto
        // tal cual -- lo normaliza (reordena las claves y añade espacios
        // tras los dos puntos). Una aserción del tipo
        // contains("\"horaSalida\":null") pasa en el test unitario, que
        // ve la salida cruda de Jackson, y falla aquí. Ver el comentario
        // sobre esto en TimeEntryAuditListener.
        JsonNode antes = json.readTree(fila.getValorAnterior());
        assertThat(antes.get("horaSalida").isNull()).isTrue();
        assertThat(antes.get("jornadaIncompleta").asBoolean()).isFalse();

        JsonNode despues = json.readTree(fila.getValorNuevo());
        assertThat(despues.get("horaSalida").isNull()).isFalse();
        assertThat(despues.get("jornadaIncompleta").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Una jornada abierta reciente (dentro del margen) NO se toca")
    void cierreAutomatico_jornadaReciente_noSeToca() {
        User empleado = crearEmpleado("reciente@nxtime.test");
        TimeEntry enCurso = timeEntryRepository.save(TimeEntry.builder()
                .usuario(empleado).empresa(empleado.getEmpresa())
                .horaEntrada(Instant.now().minus(2, ChronoUnit.HOURS)).build());

        scheduler.cerrarJornadasOlvidadas();

        TimeEntry sinTocar = timeEntryRepository.findById(enCurso.getId()).orElseThrow();
        assertThat(sinTocar.getHoraSalida()).isNull();
        assertThat(sinTocar.isJornadaIncompleta()).isFalse();
        assertThat(timeEntryAuditRepository.findByRegistro_IdOrderByFechaHoraAsc(enCurso.getId())).isEmpty();
    }
}
