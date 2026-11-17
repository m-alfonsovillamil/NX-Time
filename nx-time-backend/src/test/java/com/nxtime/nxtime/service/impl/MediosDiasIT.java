package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AbsenceRequestDTO;
import com.nxtime.nxtime.dto.AbsenceResponse;
import com.nxtime.nxtime.dto.DailyHoursResponse;
import com.nxtime.nxtime.dto.UpdateAbsenceStatusRequest;
import com.nxtime.nxtime.dto.VacationBalanceResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.AbsenceService;
import com.nxtime.nxtime.service.DailyHoursService;
import com.nxtime.nxtime.service.JornadaTeoricaService;
import com.nxtime.nxtime.service.NonWorkingDayService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Los medios días contra Postgres de verdad (ADR 063).
 *
 * <p>Lo que no ven los unitarios: que la columna y el CHECK de V60 existen y
 * dicen lo mismo que el servicio, y que un medio día se cuenta igual en todos
 * los sitios que miran una ausencia -- 0,5 en el saldo, la mitad de lo
 * esperado ese día y esa semana, y un día que <b>se trabaja</b>, no uno «no
 * laborable».
 *
 * <p>Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class MediosDiasIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "medios_dias_it_" + System.nanoTime();
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

    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AbsenceRequestRepository absenceRequestRepository;
    @Autowired private AbsenceService absenceService;
    @Autowired private NonWorkingDayService nonWorkingDayService;
    @Autowired private DailyHoursService dailyHoursService;
    @Autowired private JornadaTeoricaService jornadaTeoricaService;

    private Company empresa;
    private User empleada;
    private User gestora;
    /** Un lunes lejano: sin festivos nacionales cerca y dentro del mismo año. */
    private LocalDate lunes;

    @BeforeEach
    void preparar() {
        empresa = companyRepository.save(Company.builder().nombre("Medios " + System.nanoTime()).build());
        empleada = persona("Ana", Role.EMPLEADO);
        gestora = persona("Gema", Role.GESTOR);
        LocalDate hoy = LocalDate.now(empresa.zona());
        lunes = hoy.plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        if (lunes.getYear() != hoy.getYear() || lunes.getMonthValue() == 12) {
            lunes = hoy.withDayOfYear(1).plusYears(1).plusMonths(2).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        }
    }

    private User persona(String nombre, Role rol) {
        return userRepository.save(User.builder().nombre(nombre).apellidos("Pruebas")
                .email(nombre.toLowerCase() + "." + System.nanoTime() + "@nxtime.test")
                .contrasena("x").rol(rol).empresa(empresa).build());
    }

    private AbsenceResponse pedirMedioDia(LocalDate dia, AbsenceType tipo) {
        return absenceService.createRequest(empleada.getEmail(),
                new AbsenceRequestDTO(dia, dia, tipo, "La tarde", true));
    }

    private void aprobar(AbsenceResponse peticion) {
        absenceService.changeRequestStatus(gestora.getEmail(), peticion.id(),
                new UpdateAbsenceStatusRequest(AbsenceStatus.APROBADA, null));
    }

    @Test
    @DisplayName("Un medio día de vacaciones cuenta 0,5: en la petición, pendiente y, aprobado, consumido")
    void cuentaLaMitadEnElSaldo() {
        VacationBalanceResponse antes = absenceService.getMyVacationBalance(empleada.getEmail(), lunes.getYear());

        AbsenceResponse pedida = pedirMedioDia(lunes, AbsenceType.VACACIONES);

        assertThat(pedida.medioDia()).isTrue();
        assertThat(pedida.diasHabiles()).isEqualTo(0.5);
        VacationBalanceResponse conPendiente = absenceService.getMyVacationBalance(empleada.getEmail(), lunes.getYear());
        assertThat(conPendiente.diasPendientes()).isEqualTo(0.5);
        assertThat(conPendiente.diasDisponibles()).isEqualTo(antes.diasDisponibles() - 0.5);

        aprobar(pedida);

        VacationBalanceResponse aprobado = absenceService.getMyVacationBalance(empleada.getEmail(), lunes.getYear());
        assertThat(aprobado.diasConsumidos()).isEqualTo(0.5);
        assertThat(aprobado.diasPendientes()).isZero();
        assertThat(aprobado.diasDisponibles()).isEqualTo(antes.diasDisponibles() - 0.5);
    }

    @Test
    @DisplayName("Una petición sin la marca sigue contando días enteros (una app anterior no la manda)")
    void sinLaMarcaEsUnDiaEntero() {
        AbsenceResponse pedida = absenceService.createRequest(empleada.getEmail(),
                new AbsenceRequestDTO(lunes, lunes, AbsenceType.VACACIONES, "Un día"));

        assertThat(pedida.medioDia()).isFalse();
        assertThat(pedida.diasHabiles()).isEqualTo(1);
    }

    @Test
    @DisplayName("Medio día de un rango, o de un día que no es hábil, no se puede pedir")
    void soloDeUnDiaHabil() {
        assertThatThrownBy(() -> absenceService.createRequest(empleada.getEmail(),
                new AbsenceRequestDTO(lunes, lunes.plusDays(1), AbsenceType.VACACIONES, null, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("único día");
        LocalDate sabado = lunes.plusDays(5);
        assertThatThrownBy(() -> pedirMedioDia(sabado, AbsenceType.ASUNTOS_PROPIOS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no es hábil");
        assertThat(absenceRequestRepository.findByUsuario(empleada)).isEmpty();
    }

    @Test
    @DisplayName("La base tampoco deja guardar un medio día de varios días (CHECK de V60)")
    void elCheckDeLaBase() {
        AbsenceRequest deDosDias = AbsenceRequest.builder()
                .usuario(empleada).empresa(empresa).tipo(AbsenceType.VACACIONES)
                .fechaInicio(lunes).fechaFin(lunes.plusDays(1)).medioDia(true).build();

        assertThatThrownBy(() -> absenceRequestRepository.saveAndFlush(deDosDias))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Con el medio día aprobado, ese día SE TRABAJA: no es «no laborable», y se espera la mitad")
    void eseDiaSeTrabajaLaMitad() {
        LocalDate miercoles = lunes.plusDays(2);
        aprobar(pedirMedioDia(miercoles, AbsenceType.VACACIONES));

        // Ni la app pregunta antes de fichar ni se avisa al gestor.
        assertThat(nonWorkingDayService.motivo(empleada, miercoles)).isEmpty();
        assertThat(nonWorkingDayService.motivosEnRango(empleada, lunes, lunes.plusDays(6))).isEmpty();
        assertThat(nonWorkingDayService.mediosDiasEnRango(empleada, lunes, lunes.plusDays(6)))
                .containsOnlyKeys(miercoles);
        assertThat(nonWorkingDayService.mediosDiasDelDia(java.util.List.of(empleada, gestora), miercoles))
                .containsOnlyKeys(empleada.getId());

        // El gráfico de inicio: 40 h semanales son 8 h al día, y ese día 4.
        DailyHoursResponse elMartes = dailyHoursService.horasPorDia(empleada.getEmail(), lunes.plusDays(1), lunes.plusDays(1)).get(0);
        DailyHoursResponse elMiercoles = dailyHoursService.horasPorDia(empleada.getEmail(), miercoles, miercoles).get(0);
        assertThat(elMartes.minutosEsperados()).isEqualTo(480);
        assertThat(elMiercoles.minutosEsperados()).isEqualTo(240);
        assertThat(elMiercoles.ausencia()).isEqualTo("Vacaciones (medio día)");

        // Y la semana, contra la que se miden las horas extra: cuatro días y medio.
        assertThat(jornadaTeoricaService.minutosTeoricosSemana(empleada, lunes)).isEqualTo(36 * 60);
        assertThat(jornadaTeoricaService.dia(empleada, miercoles).medioDia()).isTrue();
    }

    @Test
    @DisplayName("Un medio día pendiente no cambia lo que se espera: puede acabar rechazado")
    void pendienteNoCuentaParaLoEsperado() {
        LocalDate miercoles = lunes.plusDays(2);
        pedirMedioDia(miercoles, AbsenceType.ASUNTOS_PROPIOS);

        assertThat(nonWorkingDayService.mediosDiasEnRango(empleada, lunes, lunes.plusDays(6))).isEmpty();
        assertThat(jornadaTeoricaService.minutosTeoricosSemana(empleada, lunes)).isEqualTo(40 * 60);
    }

    @Test
    @DisplayName("Dos medios días el mismo día son un día entero: el segundo se solapa")
    void dosMediosDiasSeSolapan() {
        pedirMedioDia(lunes, AbsenceType.VACACIONES);

        assertThatThrownBy(() -> pedirMedioDia(lunes, AbsenceType.ASUNTOS_PROPIOS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("se solapa");
    }

    @Test
    @DisplayName("Con medio día de saldo libre se puede pedir medio día, pero no uno entero")
    void elSaldoSeAgotaDeMedioEnMedio() {
        // 22 días por defecto: 21 enteros y medio dejan medio día libre.
        absenceRequestRepository.save(AbsenceRequest.builder()
                .usuario(empleada).empresa(empresa).tipo(AbsenceType.VACACIONES)
                .fechaInicio(lunes.plusWeeks(1)).fechaFin(lunes.plusWeeks(5)).build());
        // Cuatro semanas y un lunes: 21 hábiles menos los festivos que caigan. Se
        // ajusta con medios días sueltos hasta dejar exactamente 0,5.
        double libres = absenceService.getMyVacationBalance(empleada.getEmail(), lunes.getYear()).diasDisponibles();
        LocalDate dia = lunes;
        while (libres > 0.5) {
            pedirMedioDia(dia, AbsenceType.VACACIONES);
            libres -= 0.5;
            dia = dia.plusDays(1);
        }
        LocalDate libre = lunes.plusDays(4);
        assertThat(dia).isBeforeOrEqualTo(libre);

        assertThatThrownBy(() -> absenceService.createRequest(empleada.getEmail(),
                new AbsenceRequestDTO(libre, libre, AbsenceType.VACACIONES, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pides 1 días hábiles y te quedan 0,5");
        assertThat(pedirMedioDia(libre, AbsenceType.VACACIONES).diasHabiles()).isEqualTo(0.5);
        assertThat(absenceService.getMyVacationBalance(empleada.getEmail(), lunes.getYear()).diasDisponibles()).isZero();
    }
}
