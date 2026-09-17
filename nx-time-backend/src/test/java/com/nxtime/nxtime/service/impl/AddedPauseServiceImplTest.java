package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.domain.AddedPause;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.CorrectionRequest;
import com.nxtime.nxtime.domain.CorrectionStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AddPauseRequest;
import com.nxtime.nxtime.dto.CorrectionRequestDTO;
import com.nxtime.nxtime.dto.CorrectionResponse;
import com.nxtime.nxtime.dto.SimpleUserDTO;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.AddedPauseRepository;
import com.nxtime.nxtime.repository.CorrectionRequestRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.service.AddedPauseService;
import com.nxtime.nxtime.service.CorrectionService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Pausas añadidas a posteriori (ADR 015): sobre todo, <b>quién va directo y
 * quién pasa por aprobación</b>, que es la decisión que hace defendible todo
 * lo demás.
 *
 * El reloj va fijo a las 18:00 del 1 de junio en Madrid (16:00 UTC, horario
 * de verano): así "hoy" y "ayer" no dependen del día en que se ejecute.
 */
@ExtendWith(MockitoExtension.class)
class AddedPauseServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-06-01T16:00:00Z");

    @Mock
    private TimeEntryRepository timeEntryRepository;
    @Mock
    private AddedPauseRepository addedPauseRepository;
    @Mock
    private CorrectionRequestRepository correctionRepository;
    @Mock
    private CorrectionService correctionService;
    @Mock
    private TimeEntrySnapshotSerializer snapshotSerializer;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AddedPauseServiceImpl service;
    private Company empresa;
    private User empleada;
    private User otra;

    @BeforeEach
    void setUp() {
        service = new AddedPauseServiceImpl(timeEntryRepository, addedPauseRepository, correctionRepository,
                correctionService, snapshotSerializer, eventPublisher, org.mockito.Mockito.mock(com.nxtime.nxtime.service.ProjectAllocationService.class),
                Clock.fixed(AHORA, ZoneOffset.UTC));
        empresa = Company.builder().id(1L).nombre("TechCorp").build();
        empleada = User.builder().id(10L).email("ana@test").nombre("Ana")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build();
        otra = User.builder().id(11L).email("javi@test").nombre("Javi")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build();
        lenient().when(snapshotSerializer.toJson(any())).thenReturn("{}");
    }

    private TimeEntry fichaje(User dueno, String entrada, String salida) {
        TimeEntry fichaje = TimeEntry.builder().id(5L).usuario(dueno).empresa(empresa)
                .horaEntrada(Instant.parse(entrada))
                .horaSalida(salida != null ? Instant.parse(salida) : null)
                .build();
        when(timeEntryRepository.findById(5L)).thenReturn(Optional.of(fichaje));
        return fichaje;
    }

    private AddPauseRequest comida(String inicio, String fin) {
        return new AddPauseRequest(Instant.parse(inicio), Instant.parse(fin), "Olvidé fichar la comida");
    }

    private void alGuardarDevolverLoMismo() {
        when(addedPauseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(timeEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------
    // Vía directa
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Jornada abierta: se aplica en el acto y el contador baja el tiempo trabajado")
    void jornadaAbierta_vaDirecta() {
        TimeEntry abierta = fichaje(empleada, "2026-06-01T07:00:00Z", null);
        when(correctionRepository.findVivaDelRegistro(5L)).thenReturn(Optional.empty());
        alGuardarDevolverLoMismo();

        AddedPauseService.Resultado resultado =
                service.anadir(5L, comida("2026-06-01T11:00:00Z", "2026-06-01T12:00:00Z"), empleada);

        assertThat(resultado.aplicada()).isTrue();
        assertThat(resultado.correccion()).isNull();
        assertThat(abierta.getSegundosPausaAcumulados()).isEqualTo(3600);
        verify(correctionService, never()).solicitar(anyLong(), any(), any());
    }

    /*
     * Un turno de noche que empezó AYER y sigue abierto es "mi jornada de
     * ahora". Si la regla fuera solo "hoy o ayer", este caso iría por
     * aprobación para algo que la persona está viviendo en este momento.
     */
    @Test
    @DisplayName("Un turno de noche abierto que empezó ayer también va directo")
    void turnoDeNocheAbierto_vaDirecto() {
        fichaje(empleada, "2026-05-31T20:00:00Z", null);
        assertThat(service.esViaDirecta(timeEntryRepository.findById(5L).orElseThrow())).isTrue();
    }

    @Test
    @DisplayName("Jornada ya cerrada que empezó hoy: directa")
    void cerradaDeHoy_vaDirecta() {
        fichaje(empleada, "2026-06-01T07:00:00Z", "2026-06-01T15:00:00Z");
        assertThat(service.esViaDirecta(timeEntryRepository.findById(5L).orElseThrow())).isTrue();
    }

    @Test
    @DisplayName("Una pausa añadida queda en el libro con su motivo y su autora")
    void quedaEnElLibro() {
        fichaje(empleada, "2026-06-01T07:00:00Z", null);
        when(correctionRepository.findVivaDelRegistro(5L)).thenReturn(Optional.empty());
        alGuardarDevolverLoMismo();

        service.anadir(5L, comida("2026-06-01T11:00:00Z", "2026-06-01T12:00:00Z"), empleada);

        ArgumentCaptor<AddedPause> guardada = ArgumentCaptor.forClass(AddedPause.class);
        verify(addedPauseRepository).save(guardada.capture());
        assertThat(guardada.getValue().getMotivo()).isEqualTo("Olvidé fichar la comida");
        assertThat(guardada.getValue().getCreadaPor()).isEqualTo(empleada);
        assertThat(guardada.getValue().getSolicitud()).isNull();
    }

    // ------------------------------------------------------------------
    // Vía de aprobación
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Día pasado: NO toca el fichaje, lo pide como corrección con las horas tal cual")
    void diaPasado_pasaPorAprobacion() {
        TimeEntry deAyer = fichaje(empleada, "2026-05-31T07:00:00Z", "2026-05-31T15:00:00Z");
        when(correctionService.solicitar(eq(5L), any(), eq(empleada))).thenReturn(new CorrectionResponse(
                77L, 5L, new SimpleUserDTO("Ana"), new SimpleUserDTO("Ana"),
                deAyer.getHoraEntrada(), deAyer.getHoraSalida(), deAyer.getHoraEntrada(), deAyer.getHoraSalida(),
                Instant.parse("2026-05-31T11:00:00Z"), Instant.parse("2026-05-31T12:00:00Z"),
                "Olvidé fichar la comida", CorrectionStatus.PENDIENTE,
                null, null, null, null, AHORA, false, false, java.util.List.of()));

        service.anadir(5L, comida("2026-05-31T11:00:00Z", "2026-05-31T12:00:00Z"), empleada);

        ArgumentCaptor<CorrectionRequestDTO> pedida = ArgumentCaptor.forClass(CorrectionRequestDTO.class);
        verify(correctionService).solicitar(eq(5L), pedida.capture(), eq(empleada));
        // Las horas no cambian: lo único que se propone es la pausa.
        assertThat(pedida.getValue().horaEntrada()).isEqualTo(deAyer.getHoraEntrada());
        assertThat(pedida.getValue().horaSalida()).isEqualTo(deAyer.getHoraSalida());
        assertThat(pedida.getValue().pausaInicio()).isEqualTo(Instant.parse("2026-05-31T11:00:00Z"));
        // Y el fichaje de ayer, intacto.
        assertThat(deAyer.getSegundosPausaAcumulados()).isZero();
        verify(addedPauseRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Lo que no se puede
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Añadir una pausa al fichaje de otra persona no existe por esta vía")
    void fichajeAjeno_da403() {
        fichaje(otra, "2026-06-01T07:00:00Z", null);

        assertThatThrownBy(() ->
                service.anadir(5L, comida("2026-06-01T11:00:00Z", "2026-06-01T12:00:00Z"), empleada))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("Con una corrección esperando, la vía directa no suma nada")
    void conCorreccionViva_falla() {
        fichaje(empleada, "2026-06-01T07:00:00Z", null);
        when(correctionRepository.findVivaDelRegistro(5L))
                .thenReturn(Optional.of(CorrectionRequest.builder().build()));

        assertThatThrownBy(() ->
                service.anadir(5L, comida("2026-06-01T11:00:00Z", "2026-06-01T12:00:00Z"), empleada))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("corrección sin resolver");
        verify(addedPauseRepository, never()).save(any());
    }

    /*
     * Deshacer una pausa SUBE el tiempo trabajado. Sobre la jornada abierta
     * no ha llegado a ningún informe; una vez cerrada, ya no es autoservicio.
     */
    @Test
    @DisplayName("Deshacer una pausa sobre una jornada cerrada se rechaza")
    void deshacerEnJornadaCerrada_falla() {
        fichaje(empleada, "2026-06-01T07:00:00Z", "2026-06-01T15:00:00Z");

        assertThatThrownBy(() -> service.anular(5L, 99L, empleada))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cerrada");
    }

    @Test
    @DisplayName("Deshacer NO borra la pausa: la marca anulada y devuelve los segundos")
    void deshacer_marcaYDevuelveLosSegundos() {
        TimeEntry abierta = fichaje(empleada, "2026-06-01T07:00:00Z", null);
        abierta.setSegundosPausaAcumulados(3600);
        AddedPause pausa = AddedPause.builder().id(99L).registro(abierta)
                .inicio(Instant.parse("2026-06-01T11:00:00Z")).fin(Instant.parse("2026-06-01T12:00:00Z"))
                .motivo("Comida").build();
        when(addedPauseRepository.findById(99L)).thenReturn(Optional.of(pausa));
        alGuardarDevolverLoMismo();

        service.anular(5L, 99L, empleada);

        assertThat(pausa.isAnulada()).isTrue();
        assertThat(pausa.getAnuladaPor()).isEqualTo(empleada);
        assertThat(abierta.getSegundosPausaAcumulados()).isZero();
        verify(addedPauseRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Las pausas de otra persona solo las ve quien puede ver el equipo")
    void verPausasAjenas_sinPermiso_da403() {
        fichaje(otra, "2026-06-01T07:00:00Z", null);

        assertThatThrownBy(() -> service.deLaJornada(5L, empleada))
                .isInstanceOf(TenantAccessException.class);
        verify(addedPauseRepository, never()).findByRegistroAndAnuladaFalseOrderByInicioAsc(any());
    }

    @Test
    @DisplayName("Sin pausas añadidas, la lista viene vacía y no null")
    void sinPausas_listaVacia() {
        fichaje(empleada, "2026-06-01T07:00:00Z", null);
        when(addedPauseRepository.findByRegistroAndAnuladaFalseOrderByInicioAsc(any())).thenReturn(List.of());

        assertThat(service.deLaJornada(5L, empleada)).isEmpty();
    }
}
