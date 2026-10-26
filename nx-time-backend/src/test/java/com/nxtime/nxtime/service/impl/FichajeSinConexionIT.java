package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.nxtime.nxtime.audit.VerificadorDeAuditoria;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.FichajeRecibido;
import com.nxtime.nxtime.domain.OrigenDelFichaje;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.SinConexionDtos.Fichaje;
import com.nxtime.nxtime.dto.SinConexionDtos.Resultado;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.scheduled.IncompleteTimeEntryScheduler;
import com.nxtime.nxtime.service.TimeEntryService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Fichar sin conexión contra PostgreSQL de verdad (ADR 054): la hora real, el
 * reenvío, la jornada que cerró el sistema mientras el móvil no tenía red, lo
 * que llega pasadas 72 h, lo que no se acepta, y que la cadena de auditoría
 * sigue intacta después de todo eso.
 *
 * Requisito: `docker compose up -d postgres` (ver ApiContractTest).
 */
@SpringBootTest
class FichajeSinConexionIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "sin_conexion_it_" + System.nanoTime();
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
    private RecepcionDeFichajesSinConexion recepcion;
    @Autowired
    private TimeEntryService timeEntryService;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private IncompleteTimeEntryScheduler cierreNocturno;
    @Autowired
    private VerificadorDeAuditoria verificador;
    @Autowired
    private JdbcTemplate jdbc;

    private User persona(Company empresa, String nombre, Role rol) {
        return userRepository.save(User.builder()
                .email(nombre.toLowerCase() + System.nanoTime() + "@nxtime.test")
                .nombre(nombre).contrasena("hash").rol(rol).empresa(empresa).activo(true).build());
    }

    private Company empresa() {
        return companyRepository.save(Company.builder().nombre("Sin conexión " + System.nanoTime()).build());
    }

    /** Un fichaje hecho hace {@code hace}, medido con el reloj del móvil que no se toca. */
    private static Fichaje hecho(TimeEntryAction tipo, Duration hace) {
        return new Fichaje(UUID.randomUUID(), tipo, null, Instant.now().minus(hace), hace.toMillis());
    }

    private TimeEntry ultimaDe(User persona) {
        return timeEntryRepository.findFirstByUsuarioAndAnuladoFalseOrderByHoraEntradaDesc(persona).orElseThrow();
    }

    private List<String> motivosDe(long registroId) {
        return jdbc.queryForList(
                "SELECT motivo FROM auditoria_fichaje WHERE registro_id = ? ORDER BY id", String.class, registroId);
    }

    @Test
    @DisplayName("Entrada, pausa y salida sin conexión: llegan con la hora en que ocurrieron, y la cadena sigue intacta")
    void unaJornadaEnteraSinConexion() {
        Company empresa = empresa();
        User tecnica = persona(empresa, "Tecnica", Role.EMPLEADO);

        List<Resultado> resultados = recepcion.recibir(tecnica.getEmail(), List.of(
                hecho(TimeEntryAction.INICIO, Duration.ofHours(9)),
                hecho(TimeEntryAction.PAUSA_INICIO, Duration.ofHours(5)),
                hecho(TimeEntryAction.PAUSA_FIN, Duration.ofHours(4)),
                hecho(TimeEntryAction.FIN, Duration.ofHours(1))));

        assertThat(resultados).extracting(Resultado::resultado).containsOnly(FichajeRecibido.Resultado.APLICADO);
        TimeEntry jornada = ultimaDe(tecnica);
        assertThat(jornada.getHoraEntrada()).isCloseTo(Instant.now().minus(9, ChronoUnit.HOURS), within(1, ChronoUnit.MINUTES));
        assertThat(jornada.getHoraSalida()).isCloseTo(Instant.now().minus(1, ChronoUnit.HOURS), within(1, ChronoUnit.MINUTES));
        assertThat(jornada.getSegundosPausaAcumulados()).isBetween(3590L, 3610L);
        assertThat(jornada.getOrigen()).isEqualTo(OrigenDelFichaje.APP_SIN_CONEXION);
        assertThat(jornada.isRevisarPorLlegarTarde()).isFalse();
        assertThat(motivosDe(jornada.getId())).allMatch(m -> m.contains("Fichado sin conexión"));

        assertThat(verificador.verificarPara(empresa.getId()).intacta()).isTrue();
    }

    @Test
    @DisplayName("Si la app reenvía el mismo fichaje, se contesta lo mismo y no se aplica dos veces")
    void elReenvioNoDuplica() {
        User tecnico = persona(empresa(), "Tecnico", Role.EMPLEADO);
        Fichaje entrada = hecho(TimeEntryAction.INICIO, Duration.ofHours(2));

        Resultado primera = recepcion.recibir(tecnico.getEmail(), List.of(entrada)).get(0);
        Resultado segunda = recepcion.recibir(tecnico.getEmail(), List.of(entrada)).get(0);

        assertThat(primera.yaRecibido()).isFalse();
        assertThat(segunda.yaRecibido()).isTrue();
        assertThat(segunda.resultado()).isEqualTo(FichajeRecibido.Resultado.APLICADO);
        assertThat(segunda.registroId()).isEqualTo(primera.registroId());
        assertThat(timeEntryRepository.findByUsuarioOrderByHoraEntradaAsc(tecnico)).hasSize(1);
    }

    @Test
    @DisplayName("La salida de una jornada que cerró el sistema mientras no había red la reabre y pone la hora real")
    void laSalidaTardiaCorrigeElCierreAutomatico() {
        Company empresa = empresa();
        User tecnico = persona(empresa, "Olvidon", Role.EMPLEADO);
        // Entró con red hace 30 h; la pausa y la salida se quedaron en el móvil.
        timeEntryRepository.save(TimeEntry.builder()
                .usuario(tecnico).empresa(empresa).origen(OrigenDelFichaje.APP)
                .horaEntrada(Instant.now().minus(30, ChronoUnit.HOURS))
                .build());
        cierreNocturno.cerrarJornadasOlvidadas();
        assertThat(ultimaDe(tecnico).isJornadaIncompleta()).isTrue();

        List<Resultado> resultados = recepcion.recibir(tecnico.getEmail(), List.of(
                hecho(TimeEntryAction.PAUSA_INICIO, Duration.ofHours(26)),
                hecho(TimeEntryAction.PAUSA_FIN, Duration.ofHours(25)),
                hecho(TimeEntryAction.FIN, Duration.ofHours(22))));

        assertThat(resultados).extracting(Resultado::resultado).containsOnly(FichajeRecibido.Resultado.APLICADO);
        TimeEntry jornada = ultimaDe(tecnico);
        assertThat(jornada.isJornadaIncompleta()).isFalse();
        assertThat(jornada.getHoraSalida()).isCloseTo(Instant.now().minus(22, ChronoUnit.HOURS), within(1, ChronoUnit.MINUTES));
        assertThat(jornada.getSegundosPausaAcumulados()).isBetween(3590L, 3610L);
        assertThat(motivosDe(jornada.getId())).anyMatch(m -> m.startsWith("Reabierta"));
        assertThat(timeEntryRepository.contarIncidenciasAbiertas(empresa)).isZero();
        assertThat(verificador.verificarPara(empresa.getId()).intacta()).isTrue();
    }

    @Test
    @DisplayName("Lo que llega pasadas 72 h se aplica, pero cuenta como incidencia hasta que un gestor lo da por bueno")
    void loQueLlegaTardeSeRevisa() {
        Company empresa = empresa();
        User tecnico = persona(empresa, "Tardon", Role.EMPLEADO);
        User gestora = persona(empresa, "Gestora", Role.GESTOR);

        List<Resultado> resultados = recepcion.recibir(tecnico.getEmail(), List.of(
                hecho(TimeEntryAction.INICIO, Duration.ofHours(80)),
                hecho(TimeEntryAction.FIN, Duration.ofHours(74))));

        assertThat(resultados).extracting(Resultado::resultado).containsOnly(FichajeRecibido.Resultado.A_REVISAR);
        TimeEntry jornada = ultimaDe(tecnico);
        assertThat(jornada.isRevisarPorLlegarTarde()).isTrue();
        assertThat(timeEntryRepository.contarIncidenciasAbiertas(empresa)).isEqualTo(1);

        assertThatThrownBy(() -> timeEntryService.darPorBueno(tecnico.getEmail(), jornada.getId()))
                .as("su dueño no se la revisa si hay alguien más que pueda")
                .isInstanceOf(BusinessException.class);
        timeEntryService.darPorBueno(gestora.getEmail(), jornada.getId());

        assertThat(ultimaDe(tecnico).isRevisarPorLlegarTarde()).isFalse();
        assertThat(timeEntryRepository.contarIncidenciasAbiertas(empresa)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_fichaje WHERE registro_id = ? "
                + "AND accion = 'DADO_POR_BUENO'", Integer.class, jornada.getId())).isEqualTo(1);
        assertThat(verificador.verificarPara(empresa.getId()).intacta()).isTrue();
    }

    @Test
    @DisplayName("No se acepta lo que cae antes de lo que ya consta, lo del futuro ni lo de hace más de un mes")
    void loQueNoSeAcepta() {
        User tecnica = persona(empresa(), "Desordenada", Role.EMPLEADO);
        // Ya entró con red, ahora.
        timeEntryService.registerTimeEntry(tecnica.getEmail(), new TimeEntryRequest(TimeEntryAction.INICIO),
                OrigenDelFichaje.APP);

        List<Resultado> resultados = recepcion.recibir(tecnica.getEmail(), List.of(
                hecho(TimeEntryAction.PAUSA_INICIO, Duration.ofHours(1)),
                new Fichaje(UUID.randomUUID(), TimeEntryAction.PAUSA_INICIO, null,
                        Instant.now().plus(1, ChronoUnit.HOURS), null),
                hecho(TimeEntryAction.FIN, Duration.ofDays(40))));

        assertThat(resultados).extracting(Resultado::resultado).containsOnly(FichajeRecibido.Resultado.RECHAZADO);
        assertThat(resultados.get(0).motivo()).contains("anterior al último");
        assertThat(ultimaDe(tecnica).isEnPausa()).isFalse();

        // El rechazo también se recuerda: reenviarlo no lo vuelve a intentar.
        Resultado otraVez = recepcion.recibir(tecnica.getEmail(), List.of(
                new Fichaje(resultados.get(0).idCliente(), TimeEntryAction.PAUSA_INICIO, null, Instant.now(), 0L)))
                .get(0);
        assertThat(otraVez.yaRecibido()).isTrue();
        assertThat(otraVez.resultado()).isEqualTo(FichajeRecibido.Resultado.RECHAZADO);
    }

    @Test
    @DisplayName("Si el móvil se reinició, vale la hora de su reloj, y la auditoría lo dice")
    void trasReiniciarSeUsaLaHoraDelMovil() {
        User tecnico = persona(empresa(), "Reiniciado", Role.EMPLEADO);
        Instant aLas = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        Resultado resultado = recepcion.recibir(tecnico.getEmail(), List.of(
                new Fichaje(UUID.randomUUID(), TimeEntryAction.INICIO, null, aLas, null))).get(0);

        assertThat(resultado.resultado()).isEqualTo(FichajeRecibido.Resultado.APLICADO);
        TimeEntry jornada = ultimaDe(tecnico);
        assertThat(jornada.getHoraEntrada()).isEqualTo(aLas);
        assertThat(motivosDe(jornada.getId())).anyMatch(m -> m.contains("se reinició"));
    }
}
