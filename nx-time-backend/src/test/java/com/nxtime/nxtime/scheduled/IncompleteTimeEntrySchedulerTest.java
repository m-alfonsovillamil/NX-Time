package com.nxtime.nxtime.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nxtime.nxtime.audit.TimeEntryAuditEvent;
import com.nxtime.nxtime.audit.TimeEntrySnapshotSerializer;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unitarios del cierre automático de jornadas olvidadas (Fase 9).
 *
 * Lo que hay detrás no es cosmético: por el índice parcial único
 * uq_registros_jornada_abierta (Fase 3), una jornada sin cerrar
 * bloqueaba TODOS los fichajes futuros de ese empleado.
 */
@ExtendWith(MockitoExtension.class)
class IncompleteTimeEntrySchedulerTest {

    @Mock
    private TimeEntryRepository timeEntryRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private IncompleteTimeEntryScheduler scheduler;
    private User empleado;

    @BeforeEach
    void setUp() {
        TimeEntrySnapshotSerializer serializer = new TimeEntrySnapshotSerializer(
                new ObjectMapper().registerModule(new JavaTimeModule()));
        scheduler = new IncompleteTimeEntryScheduler(timeEntryRepository, eventPublisher, serializer);
        Company empresa = Company.builder().id(1L).build();
        empleado = User.builder().id(10L).email("empleado@nxtime.test").empresa(empresa).build();
    }

    @Test
    @DisplayName("Sin jornadas olvidadas no se toca nada ni se publica ningún evento")
    void cerrarJornadasOlvidadas_sinJornadasAbiertas_noHaceNada() {
        when(timeEntryRepository.findJornadasAbiertasAnterioresA(any())).thenReturn(List.of());

        scheduler.cerrarJornadasOlvidadas();

        verify(timeEntryRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(TimeEntryAuditEvent.class));
    }

    @Test
    @DisplayName("Una jornada olvidada se cierra, se marca como incompleta y deja traza de auditoría del sistema")
    void cerrarJornadasOlvidadas_jornadaOlvidada_laCierraYLaMarca() {
        Instant horaEntrada = Instant.now().minus(30, ChronoUnit.HOURS);
        TimeEntry olvidada = TimeEntry.builder().id(1L).usuario(empleado)
                .horaEntrada(horaEntrada).build();
        when(timeEntryRepository.findJornadasAbiertasAnterioresA(any())).thenReturn(List.of(olvidada));

        scheduler.cerrarJornadasOlvidadas();

        assertThat(olvidada.getHoraSalida()).isNotNull();
        assertThat(olvidada.isJornadaIncompleta()).isTrue();
        verify(timeEntryRepository).save(olvidada);

        ArgumentCaptor<TimeEntryAuditEvent> captor = ArgumentCaptor.forClass(TimeEntryAuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var auditRow = captor.getValue().auditRow();
        assertThat(auditRow.getAccion()).isEqualTo(AuditAction.MODIFICACION);
        // null = acción automática del sistema, no "autor desconocido".
        assertThat(auditRow.getModificadoPor()).isNull();
        assertThat(auditRow.getMotivo()).contains("Cierre automático");
        assertThat(auditRow.getValorAnterior()).contains("\"horaSalida\":null");
        assertThat(auditRow.getValorNuevo()).contains("\"jornadaIncompleta\":true");
    }

    @Test
    @DisplayName("La hora de salida se fija al límite de antigüedad, no a 'ahora'")
    void cerrarJornadasOlvidadas_horaSalidaSeFijaAlLimite_noAAhora() {
        // Jornada abierta hace 3 días: dar por buenas 72 horas
        // trabajadas sería peor que no hacer nada.
        Instant horaEntrada = Instant.now().minus(72, ChronoUnit.HOURS);
        TimeEntry olvidada = TimeEntry.builder().id(1L).usuario(empleado).horaEntrada(horaEntrada).build();
        when(timeEntryRepository.findJornadasAbiertasAnterioresA(any())).thenReturn(List.of(olvidada));

        scheduler.cerrarJornadasOlvidadas();

        long horasImputadas = ChronoUnit.HOURS.between(horaEntrada, olvidada.getHoraSalida());
        assertThat(horasImputadas).isEqualTo(16);
    }

    @Test
    @DisplayName("Una jornada olvidada EN PAUSA se cierra además saliendo del estado de pausa")
    void cerrarJornadasOlvidadas_jornadaEnPausa_saleDeLaPausa() {
        TimeEntry olvidada = TimeEntry.builder().id(1L).usuario(empleado)
                .horaEntrada(Instant.now().minus(30, ChronoUnit.HOURS))
                .enPausa(true).inicioPausaActual(Instant.now().minus(20, ChronoUnit.HOURS))
                .build();
        when(timeEntryRepository.findJornadasAbiertasAnterioresA(any())).thenReturn(List.of(olvidada));

        scheduler.cerrarJornadasOlvidadas();

        assertThat(olvidada.isEnPausa()).isFalse();
        assertThat(olvidada.getInicioPausaActual()).isNull();
    }

    @Test
    @DisplayName("Varias jornadas olvidadas se cierran todas")
    void cerrarJornadasOlvidadas_variasJornadas_lasCierraTodas() {
        TimeEntry una = TimeEntry.builder().id(1L).usuario(empleado)
                .horaEntrada(Instant.now().minus(30, ChronoUnit.HOURS)).build();
        TimeEntry otra = TimeEntry.builder().id(2L).usuario(empleado)
                .horaEntrada(Instant.now().minus(40, ChronoUnit.HOURS)).build();
        when(timeEntryRepository.findJornadasAbiertasAnterioresA(any())).thenReturn(List.of(una, otra));

        scheduler.cerrarJornadasOlvidadas();

        assertThat(una.isJornadaIncompleta()).isTrue();
        assertThat(otra.isJornadaIncompleta()).isTrue();
        verify(timeEntryRepository).save(una);
        verify(timeEntryRepository).save(otra);
    }
}
