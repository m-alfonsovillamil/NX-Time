package com.nxtime.nxtime.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.ScheduleIncident;
import com.nxtime.nxtime.domain.ScheduleIncidentStatus;
import com.nxtime.nxtime.domain.ScheduleIncidentType;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.AnalyticsRepository.PersonaProjection;
import com.nxtime.nxtime.repository.AnalyticsRepository.RetrasosProjection;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Las consultas nativas de la analítica (Fase B4) contra un PostgreSQL real.
 *
 * Obligatorio y no opcional: son SQL nativo con {@code generate_series},
 * {@code GROUPING SETS}, {@code percentile_cont}, zonas horarias y un
 * parámetro que puede ser nulo. Un test con mocks no probaría ninguna de esas
 * cosas, y cada una tiene una forma propia de fallar solo en la base.
 *
 * Marzo de 2026, antes del cambio de hora: Madrid es UTC+1.
 */
class AnalyticsRepositoryIT extends AbstractRepositoryTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 3, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 3, 31);

    @Autowired
    private AnalyticsRepository analyticsRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private EntityManager entityManager;

    private Company empresa;
    private Department ventas;
    private Department almacen;
    private User ana;
    private User bruno;
    private User carla;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Analítica S.L.").build());
        ventas = persistir(Department.builder().empresa(empresa).nombre("Ventas").build());
        almacen = persistir(Department.builder().empresa(empresa).nombre("Almacén").build());
        ana = usuario("ana", "Ana", "Pérez", ventas);
        bruno = usuario("bruno", "Bruno", null, almacen);
        carla = usuario("carla", "Carla", "Ruiz", null);
    }

    // ------------------------------------------------------------------
    // personas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("personas: cuenta desde el primer día fichado, en día de España, y sin fichajes anulados")
    void personas_primerDiaEnMadridSinAnulados() {
        // Un fichaje anulado del 20 de febrero no hace de fecha de alta.
        TimeEntry anulado = jornada(ana, "2026-02-20T08:00:00Z", "2026-02-20T16:00:00Z");
        anulado.setAnulado(true);
        timeEntryRepository.save(anulado);
        // 23:30 UTC del 2 de marzo son las 00:30 del 3 en Madrid.
        jornada(ana, "2026-03-02T23:30:00Z", "2026-03-03T07:00:00Z");
        jornada(bruno, "2026-01-15T08:00:00Z", "2026-01-15T16:00:00Z");

        List<PersonaProjection> personas = analyticsRepository.personas(empresa.getId(), null, DESDE, HASTA);

        assertThat(personas)
                .extracting(PersonaProjection::getNombre, PersonaProjection::getDepartamento,
                        PersonaProjection::getPrimerDia)
                .containsExactly(
                        tuple("Ana Pérez", "Ventas", LocalDate.of(2026, 3, 3)),
                        tuple("Bruno", "Almacén", LocalDate.of(2026, 1, 15)));
    }

    @Test
    @DisplayName("personas: el departamento nulo trae la empresa; uno concreto, solo el suyo")
    void personas_filtroDeDepartamento() {
        jornada(ana, "2026-03-02T08:00:00Z", "2026-03-02T16:00:00Z");
        jornada(bruno, "2026-03-02T08:00:00Z", "2026-03-02T16:00:00Z");
        jornada(carla, "2026-03-02T08:00:00Z", "2026-03-02T16:00:00Z");

        assertThat(analyticsRepository.personas(empresa.getId(), null, DESDE, HASTA)).hasSize(3);
        assertThat(analyticsRepository.personas(empresa.getId(), almacen.getId(), DESDE, HASTA))
                .extracting(PersonaProjection::getUsuarioId)
                .containsExactly(bruno.getId());
    }

    @Test
    @DisplayName("personas: fuera quien empezó después del periodo, quien se fue antes y quien es de otra empresa")
    void personas_fueraDelPeriodoOEmpresa() {
        jornada(ana, "2026-04-02T08:00:00Z", "2026-04-02T16:00:00Z");
        jornada(bruno, "2026-01-02T08:00:00Z", "2026-01-02T16:00:00Z");
        bruno.setActivo(false);
        bruno.setFechaBaja(Instant.parse("2026-02-10T10:00:00Z"));
        userRepository.save(bruno);
        jornada(carla, "2026-01-02T08:00:00Z", "2026-01-02T16:00:00Z");
        carla.setFechaBaja(Instant.parse("2026-03-10T10:00:00Z"));
        userRepository.save(carla);

        Company otra = companyRepository.save(Company.builder().nombre("Otra").build());
        User ajena = userRepository.save(User.builder().email("ajena@nxtime.test").nombre("Ajena")
                .contrasena("hash").rol(Role.EMPLEADO).empresa(otra).build());
        timeEntryRepository.save(TimeEntry.builder().usuario(ajena).empresa(otra)
                .horaEntrada(Instant.parse("2026-03-02T08:00:00Z"))
                .horaSalida(Instant.parse("2026-03-02T16:00:00Z")).build());

        List<PersonaProjection> personas = analyticsRepository.personas(empresa.getId(), null, DESDE, HASTA);

        assertThat(personas)
                .extracting(PersonaProjection::getUsuarioId, PersonaProjection::getDiaDeBaja)
                .containsExactly(tuple(carla.getId(), LocalDate.of(2026, 3, 10)));
    }

    // ------------------------------------------------------------------
    // días con jornada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("diasConJornada: un día por persona y día de España, abiertas incluidas y anuladas no")
    void diasConJornada() {
        jornada(ana, "2026-03-02T08:00:00Z", "2026-03-02T12:00:00Z");
        jornada(ana, "2026-03-02T13:00:00Z", "2026-03-02T17:00:00Z");
        jornada(ana, "2026-03-02T23:30:00Z", null);
        TimeEntry anulado = jornada(bruno, "2026-03-04T08:00:00Z", "2026-03-04T16:00:00Z");
        anulado.setAnulado(true);
        timeEntryRepository.save(anulado);

        List<AnalyticsRepository.UsuarioDiaProjection> dias = analyticsRepository.diasConJornada(
                List.of(ana.getId(), bruno.getId()),
                Instant.parse("2026-02-28T23:00:00Z"), Instant.parse("2026-03-31T22:00:00Z"));

        assertThat(dias)
                .extracting(AnalyticsRepository.UsuarioDiaProjection::getUsuarioId,
                        AnalyticsRepository.UsuarioDiaProjection::getDia)
                .containsExactlyInAnyOrder(
                        tuple(ana.getId(), LocalDate.of(2026, 3, 2)),
                        tuple(ana.getId(), LocalDate.of(2026, 3, 3)));
    }

    // ------------------------------------------------------------------
    // ausencias
    // ------------------------------------------------------------------

    @Test
    @DisplayName("diasDeAusencia: día a día, recortada al periodo, solo aprobadas y sin contar dos veces un día")
    void diasDeAusencia() {
        // Del 27 de febrero al 3 de marzo: solo cuentan el 1, el 2 y el 3.
        ausencia(ana, "2026-02-27", "2026-03-03", AbsenceType.MEDICO, AbsenceStatus.APROBADA);
        // Se pisa con la anterior el día 3: sale una sola fila, la de la más antigua.
        ausencia(ana, "2026-03-03", "2026-03-03", AbsenceType.OTROS, AbsenceStatus.APROBADA);
        ausencia(ana, "2026-03-10", "2026-03-10", AbsenceType.VACACIONES, AbsenceStatus.PENDIENTE);
        ausencia(ana, "2026-03-11", "2026-03-11", AbsenceType.VACACIONES, AbsenceStatus.RECHAZADA);
        ausencia(bruno, "2026-03-30", "2026-04-05", AbsenceType.VACACIONES, AbsenceStatus.APROBADA);

        List<AnalyticsRepository.AusenciaDiaProjection> dias =
                analyticsRepository.diasDeAusencia(List.of(ana.getId(), bruno.getId()), DESDE, HASTA);

        assertThat(dias)
                .extracting(AnalyticsRepository.AusenciaDiaProjection::getUsuarioId,
                        AnalyticsRepository.AusenciaDiaProjection::getDia,
                        AnalyticsRepository.AusenciaDiaProjection::getTipo)
                .containsExactlyInAnyOrder(
                        tuple(ana.getId(), LocalDate.of(2026, 3, 1), "MEDICO"),
                        tuple(ana.getId(), LocalDate.of(2026, 3, 2), "MEDICO"),
                        tuple(ana.getId(), LocalDate.of(2026, 3, 3), "MEDICO"),
                        tuple(bruno.getId(), LocalDate.of(2026, 3, 30), "VACACIONES"),
                        tuple(bruno.getId(), LocalDate.of(2026, 3, 31), "VACACIONES"));
    }

    @Test
    @DisplayName("ausenciasAceptadas: solo las ausencias de cuadrante cuya explicación se aceptó")
    void ausenciasAceptadas() {
        incidencia(ana, "2026-03-02", ScheduleIncidentType.AUSENCIA, 480, ScheduleIncidentStatus.ACEPTADA);
        incidencia(ana, "2026-03-03", ScheduleIncidentType.AUSENCIA, 480, ScheduleIncidentStatus.RECHAZADA);
        incidencia(ana, "2026-03-04", ScheduleIncidentType.AUSENCIA, 480, ScheduleIncidentStatus.JUSTIFICADA);
        incidencia(ana, "2026-03-05", ScheduleIncidentType.RETRASO, 20, ScheduleIncidentStatus.ACEPTADA);

        assertThat(analyticsRepository.ausenciasAceptadas(List.of(ana.getId()), DESDE, HASTA))
                .extracting(AnalyticsRepository.UsuarioDiaProjection::getDia)
                .containsExactly(LocalDate.of(2026, 3, 2));
    }

    // ------------------------------------------------------------------
    // retrasos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("retrasos: total, departamento y persona en una consulta, con media, mediana y tramos")
    void retrasos_tresNiveles() {
        incidencia(ana, "2026-03-02", ScheduleIncidentType.RETRASO, 12, ScheduleIncidentStatus.PENDIENTE);
        incidencia(ana, "2026-03-03", ScheduleIncidentType.RETRASO, 20, ScheduleIncidentStatus.ACEPTADA);
        incidencia(ana, "2026-03-04", ScheduleIncidentType.RETRASO, 90, ScheduleIncidentStatus.RECHAZADA);
        incidencia(carla, "2026-03-02", ScheduleIncidentType.RETRASO, 40, ScheduleIncidentStatus.PENDIENTE);
        // Ni otra clase de incidencia ni un retraso fuera del periodo.
        incidencia(ana, "2026-03-05", ScheduleIncidentType.SALIDA_ANTICIPADA, 30, ScheduleIncidentStatus.PENDIENTE);
        incidencia(ana, "2026-04-01", ScheduleIncidentType.RETRASO, 15, ScheduleIncidentStatus.PENDIENTE);

        List<RetrasosProjection> filas = analyticsRepository.retrasos(
                List.of(ana.getId(), bruno.getId(), carla.getId()), DESDE, HASTA);

        RetrasosProjection total = nivel(filas, "EMPRESA", null, null);
        assertThat(total.getRetrasos()).isEqualTo(4);
        assertThat(total.getMedia()).isEqualTo(40.5);
        // 12, 20, 40, 90: la mediana interpola entre 20 y 40.
        assertThat(total.getMediana()).isEqualTo(30.0);
        assertThat(total.getHasta30()).isEqualTo(2);
        assertThat(total.getMasDe30()).isEqualTo(2);

        RetrasosProjection deVentas = nivel(filas, "DEPARTAMENTO", ventas.getId(), null);
        assertThat(deVentas.getRetrasos()).isEqualTo(3);
        assertThat(deVentas.getMediana()).isEqualTo(20.0);
        // Carla no tiene departamento: su fila es la de departamento nulo, no el total.
        assertThat(nivel(filas, "DEPARTAMENTO", null, null).getRetrasos()).isEqualTo(1);

        RetrasosProjection deAna = nivel(filas, "EMPLEADO", null, ana.getId());
        assertThat(deAna.getRetrasos()).isEqualTo(3);
        assertThat(filas).noneMatch(fila -> bruno.getId() == (fila.getUsuarioId() == null ? -1 : fila.getUsuarioId()));
    }

    // ------------------------------------------------------------------
    // jornadas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("jornadas: cerradas y no anuladas, las incompletas aparte y el tiempo sin pausas")
    void jornadas() {
        TimeEntry conPausa = jornada(ana, "2026-03-02T08:00:00Z", "2026-03-02T16:00:00Z");
        conPausa.setSegundosPausaAcumulados(1800);
        timeEntryRepository.save(conPausa);
        TimeEntry incompleta = jornada(ana, "2026-03-03T08:00:00Z", "2026-03-03T10:00:00Z");
        incompleta.setJornadaIncompleta(true);
        timeEntryRepository.save(incompleta);
        jornada(ana, "2026-03-04T08:00:00Z", null);
        TimeEntry anulado = jornada(ana, "2026-03-05T08:00:00Z", "2026-03-05T16:00:00Z");
        anulado.setAnulado(true);
        timeEntryRepository.save(anulado);

        AnalyticsRepository.JornadasProjection jornadas = analyticsRepository.jornadas(
                List.of(ana.getId()), Instant.parse("2026-02-28T23:00:00Z"), Instant.parse("2026-03-31T22:00:00Z"));

        assertThat(jornadas.getJornadas()).isEqualTo(2);
        assertThat(jornadas.getIncompletas()).isEqualTo(1);
        assertThat(jornadas.getSegundos()).isEqualTo(8 * 3600 - 1800 + 2 * 3600.0);
    }

    // ------------------------------------------------------------------

    private RetrasosProjection nivel(List<RetrasosProjection> filas, String nivel, Long departamentoId, Long usuarioId) {
        return filas.stream()
                .filter(fila -> nivel.equals(fila.getNivel()))
                .filter(fila -> !"DEPARTAMENTO".equals(nivel)
                        || java.util.Objects.equals(fila.getDepartamentoId(), departamentoId))
                .filter(fila -> !"EMPLEADO".equals(nivel) || java.util.Objects.equals(fila.getUsuarioId(), usuarioId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay fila " + nivel + " " + departamentoId + " " + usuarioId));
    }

    private <T> T persistir(T entidad) {
        entityManager.persist(entidad);
        return entidad;
    }

    private User usuario(String alias, String nombre, String apellidos, Department departamento) {
        return userRepository.save(User.builder()
                .email(alias + "@nxtime.test").nombre(nombre).apellidos(apellidos).contrasena("hash")
                .rol(Role.EMPLEADO).empresa(empresa).departamento(departamento).build());
    }

    private TimeEntry jornada(User persona, String entrada, String salida) {
        return timeEntryRepository.save(TimeEntry.builder().usuario(persona).empresa(persona.getEmpresa())
                .horaEntrada(Instant.parse(entrada))
                .horaSalida(salida == null ? null : Instant.parse(salida))
                .build());
    }

    private void ausencia(User persona, String inicio, String fin, AbsenceType tipo, AbsenceStatus estado) {
        AbsenceRequest.AbsenceRequestBuilder ausencia = AbsenceRequest.builder()
                .usuario(persona).empresa(persona.getEmpresa())
                .fechaInicio(LocalDate.parse(inicio)).fechaFin(LocalDate.parse(fin))
                .tipo(tipo).estado(estado);
        if (estado != AbsenceStatus.PENDIENTE) {
            ausencia.aprobadoPor(bruno).fechaResolucion(Instant.now());
        }
        persistir(ausencia.build());
    }

    private void incidencia(
            User persona, String fecha, ScheduleIncidentType tipo, int minutos, ScheduleIncidentStatus estado) {
        ScheduleIncident.ScheduleIncidentBuilder incidencia = ScheduleIncident.builder()
                .empresa(persona.getEmpresa()).usuario(persona).fecha(LocalDate.parse(fecha))
                .tipo(tipo).minutos(minutos).horaPrevista(540).estado(estado);
        if (estado.resuelta()) {
            incidencia.resueltaPor(bruno).resueltaEn(Instant.now());
        }
        persistir(incidencia.build());
    }
}
