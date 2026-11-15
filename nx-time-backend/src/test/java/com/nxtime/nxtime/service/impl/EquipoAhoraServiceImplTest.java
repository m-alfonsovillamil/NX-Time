package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Modalidad;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.EquipoAhoraResponse;
import com.nxtime.nxtime.dto.EquipoAhoraResponse.EstadoAhora;
import com.nxtime.nxtime.dto.EquipoAhoraResponse.PersonaAhora;
import com.nxtime.nxtime.dto.TeamScheduleEntryResponse;
import com.nxtime.nxtime.dto.TheoreticalDayResponse;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.JornadaTeoricaService;
import com.nxtime.nxtime.service.NonWorkingDayService;
import com.nxtime.nxtime.service.ScheduleService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Quién está trabajando ahora (plan N, paso N7), con el reloj parado el
 * martes 17 de noviembre de 2026 a las 9:30 de Madrid.
 */
@ExtendWith(MockitoExtension.class)
class EquipoAhoraServiceImplTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 17);
    private static final Instant AHORA = ZonedDateTime.of(HOY, LocalTime.of(9, 30), MADRID).toInstant();

    @Mock private UserRepository userRepository;
    @Mock private TimeEntryRepository timeEntryRepository;
    @Mock private NonWorkingDayService nonWorkingDayService;
    @Mock private ScheduleService scheduleService;

    private EquipoAhoraServiceImpl service;
    private Company empresa;
    private User gestora;

    @BeforeEach
    void preparar() {
        service = new EquipoAhoraServiceImpl(userRepository, timeEntryRepository, nonWorkingDayService,
                scheduleService, Clock.fixed(AHORA, MADRID));
        empresa = Company.builder().id(1L).nombre("TechCorp").build();
        gestora = persona(1L, "Marta", Role.GESTOR);
    }

    private User persona(long id, String nombre, Role rol) {
        return User.builder().id(id).nombre(nombre).apellidos("Prueba").rol(rol).empresa(empresa).activo(true).build();
    }

    private static Instant a(int hora, int minuto) {
        return ZonedDateTime.of(HOY, LocalTime.of(hora, minuto), MADRID).toInstant();
    }

    private static TimeEntry jornada(User de, Instant entrada, Instant salida) {
        return TimeEntry.builder().usuario(de).horaEntrada(entrada).horaSalida(salida).build();
    }

    @Test
    @DisplayName("Cada persona en su estado, primero quien llega tarde, y los minutos de hoy sin pausas")
    void cadaUnoEnSuEstado() {
        User trabajando = persona(2L, "Ana", Role.EMPLEADO);
        User enPausa = persona(3L, "Bea", Role.EMPLEADO);
        User terminada = persona(4L, "Carla", Role.EMPLEADO);
        User tarde = persona(5L, "Dani", Role.EMPLEADO);
        User deVacaciones = persona(6L, "Eva", Role.EMPLEADO);
        User sinCuadrante = persona(7L, "Fran", Role.EMPLEADO);
        User deNoche = persona(8L, "Gael", Role.EMPLEADO);
        when(userRepository.findByEmpresaAndActivoTrue(empresa)).thenReturn(
                List.of(gestora, trabajando, enPausa, terminada, tarde, deVacaciones, sinCuadrante, deNoche));

        TimeEntry deAna = jornada(trabajando, a(8, 0), null);
        deAna.setModalidad(Modalidad.A_DISTANCIA);
        TimeEntry deBea = jornada(enPausa, a(8, 0), null);
        deBea.setEnPausa(true);
        deBea.setInicioPausaActual(a(9, 0));
        TimeEntry deCarla = jornada(terminada, a(6, 0), a(9, 0));
        deCarla.setSegundosPausaAcumulados(600);
        TimeEntry deGael = jornada(deNoche, a(0, 0).minusSeconds(2 * 3600), null);
        when(timeEntryRepository.findVivosDeUsuariosQueEmpiezanEntre(anyCollection(), eq(a(0, 0)), any()))
                .thenReturn(List.of(deAna, deBea, deCarla));
        when(timeEntryRepository.findAbiertasDeEmpresa(empresa)).thenReturn(List.of(deAna, deBea, deGael));
        when(nonWorkingDayService.motivosDelDia(anyCollection(), eq(HOY)))
                .thenReturn(Map.of(6L, new NonWorkingDayService.Motivo("Vacaciones", true)));
        when(scheduleService.equipo(HOY, gestora)).thenReturn(List.of(
                new TeamScheduleEntryResponse(5L, "Dani Prueba", dia("09:00", 480)),
                new TeamScheduleEntryResponse(6L, "Eva Prueba", dia("09:00", 480)),
                new TeamScheduleEntryResponse(1L, "Marta Prueba", dia("10:00", 480))));

        EquipoAhoraResponse foto = service.ahora(gestora);

        assertThat(foto.fecha()).isEqualTo(HOY);
        assertThat(foto.personas()).extracting(PersonaAhora::nombre).first().isEqualTo("Dani Prueba");
        assertThat(estado(foto, 5L)).returns(EstadoAhora.SIN_FICHAR, PersonaAhora::estado).returns(true, PersonaAhora::conRetraso);
        // Entra a las 10:00: todavía no llega tarde.
        assertThat(estado(foto, 1L)).returns(EstadoAhora.SIN_FICHAR, PersonaAhora::estado).returns(false, PersonaAhora::conRetraso);
        assertThat(estado(foto, 7L)).returns(EstadoAhora.SIN_FICHAR, PersonaAhora::estado).returns(null, PersonaAhora::entradaPrevista);
        assertThat(estado(foto, 2L)).returns(EstadoAhora.TRABAJANDO, PersonaAhora::estado).returns(90L, PersonaAhora::minutosHoy)
                .returns(Modalidad.A_DISTANCIA, PersonaAhora::modalidad);
        // De 8:00 a 9:30 menos la pausa en curso desde las 9:00.
        assertThat(estado(foto, 3L)).returns(EstadoAhora.EN_PAUSA, PersonaAhora::estado).returns(60L, PersonaAhora::minutosHoy);
        assertThat(estado(foto, 4L)).returns(EstadoAhora.TERMINADA, PersonaAhora::estado).returns(170L, PersonaAhora::minutosHoy);
        // Entró ayer a las 22:00 y sigue: trabajando, con toda su jornada.
        assertThat(estado(foto, 8L)).returns(EstadoAhora.TRABAJANDO, PersonaAhora::estado).returns(690L, PersonaAhora::minutosHoy);
        assertThat(estado(foto, 6L)).returns(EstadoAhora.NO_LABORABLE, PersonaAhora::estado).returns("Vacaciones", PersonaAhora::motivo)
                .returns(null, PersonaAhora::entradaPrevista);
        assertThat(foto.trabajando()).isEqualTo(2);
        assertThat(foto.enPausa()).isEqualTo(1);
        assertThat(foto.terminada()).isEqualTo(1);
        assertThat(foto.sinFichar()).isEqualTo(3);
        assertThat(foto.conRetraso()).isEqualTo(1);
        assertThat(foto.noLaborable()).isEqualTo(1);
    }

    @Test
    @DisplayName("En fin de semana no le toca a quien no tiene cuadrante; con cuadrante, manda el cuadrante")
    void finDeSemanaYDiaLibre() {
        Instant domingo = ZonedDateTime.of(LocalDate.of(2026, 11, 15), LocalTime.of(11, 0), MADRID).toInstant();
        service = new EquipoAhoraServiceImpl(userRepository, timeEntryRepository, nonWorkingDayService,
                scheduleService, Clock.fixed(domingo, MADRID));
        User sinCuadrante = persona(2L, "Ana", Role.EMPLEADO);
        User trabajaElDomingo = persona(3L, "Bea", Role.EMPLEADO);
        User libreEnCuadrante = persona(4L, "Carla", Role.EMPLEADO);
        when(userRepository.findByEmpresaAndActivoTrue(empresa))
                .thenReturn(List.of(sinCuadrante, trabajaElDomingo, libreEnCuadrante));
        when(timeEntryRepository.findVivosDeUsuariosQueEmpiezanEntre(anyCollection(), any(), any())).thenReturn(List.of());
        when(timeEntryRepository.findAbiertasDeEmpresa(empresa)).thenReturn(List.of());
        when(nonWorkingDayService.motivosDelDia(anyCollection(), any())).thenReturn(Map.of());
        when(scheduleService.equipo(any(), eq(gestora))).thenReturn(List.of(
                new TeamScheduleEntryResponse(3L, "Bea Prueba", dia("10:00", 300)),
                new TeamScheduleEntryResponse(4L, "Carla Prueba", dia(null, 0))));

        EquipoAhoraResponse foto = service.ahora(gestora);

        assertThat(estado(foto, 2L)).returns(EstadoAhora.NO_LABORABLE, PersonaAhora::estado)
                .returns("Fin de semana", PersonaAhora::motivo);
        assertThat(estado(foto, 3L)).returns(EstadoAhora.SIN_FICHAR, PersonaAhora::estado)
                .returns(true, PersonaAhora::conRetraso);
        assertThat(estado(foto, 4L)).returns(EstadoAhora.NO_LABORABLE, PersonaAhora::estado)
                .returns("Libre según su cuadrante", PersonaAhora::motivo);
    }

    private static TheoreticalDayResponse dia(String entrada, int minutos) {
        return new TheoreticalDayResponse(HOY, JornadaTeoricaService.Origen.CUADRANTE, minutos, entrada, List.of(), null, null);
    }

    private static PersonaAhora estado(EquipoAhoraResponse foto, long id) {
        return foto.personas().stream().filter(p -> p.usuarioId() == id).findFirst().orElseThrow();
    }
}
