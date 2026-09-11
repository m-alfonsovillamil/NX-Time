package com.nxtime.nxtime.service.impl;

import static com.nxtime.nxtime.domain.ScheduledTask.CIERRE_JORNADAS;
import static com.nxtime.nxtime.domain.ScheduledTask.HORAS_EXTRA;
import static com.nxtime.nxtime.domain.ScheduledTaskResult.EN_CURSO;
import static com.nxtime.nxtime.domain.ScheduledTaskResult.ERROR;
import static com.nxtime.nxtime.domain.ScheduledTaskResult.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.ScheduledTaskResult;
import com.nxtime.nxtime.domain.ScheduledTaskRun;
import com.nxtime.nxtime.dto.SystemStatusResponse;
import com.nxtime.nxtime.repository.ScheduledTaskRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unitarios del registro de tareas programadas (paso 5 del piloto).
 *
 * Las horas se escriben en hora española a propósito: es lo que tiene que
 * respetar la comprobación, cambio de hora incluido.
 */
@ExtendWith(MockitoExtension.class)
class TaskMonitorServiceImplTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Mock
    private ScheduledTaskRunRepository repository;

    /** El resultado con el que se guardó cada vez, en orden. */
    private final List<ScheduledTaskResult> guardados = new ArrayList<>();

    private static Instant madrid(String fechaHora) {
        return LocalDateTime.parse(fechaHora).atZone(MADRID).toInstant();
    }

    private TaskMonitorServiceImpl servicioA(String fechaHoraMadrid) {
        return new TaskMonitorServiceImpl(repository, Clock.fixed(madrid(fechaHoraMadrid), ZoneOffset.UTC));
    }

    /*
     * El servicio guarda la MISMA instancia dos veces (al empezar y al
     * terminar), así que un ArgumentCaptor vería dos veces el estado final.
     * Se apunta el resultado en el momento de cada save.
     */
    private void apuntarCadaGuardado() {
        when(repository.save(any(ScheduledTaskRun.class))).thenAnswer(invocacion -> {
            ScheduledTaskRun ejecucion = invocacion.getArgument(0);
            guardados.add(ejecucion.getResultado());
            return ejecucion;
        });
    }

    @Test
    @DisplayName("Un trabajo que sale bien queda registrado EN_CURSO al empezar y OK con su resumen al terminar")
    void ejecutar_trabajoCorrecto_registraEnCursoYDespuesOk() {
        apuntarCadaGuardado();

        servicioA("2026-09-12T03:00:00").ejecutar(CIERRE_JORNADAS, () -> "Cerradas 2 jornadas sin fichaje de salida.");

        assertThat(guardados).containsExactly(EN_CURSO, OK);
        ArgumentCaptor<ScheduledTaskRun> captor = ArgumentCaptor.forClass(ScheduledTaskRun.class);
        verify(repository, times(2)).save(captor.capture());
        ScheduledTaskRun ejecucion = captor.getValue();
        assertThat(ejecucion.getTarea()).isEqualTo(CIERRE_JORNADAS);
        assertThat(ejecucion.getInicio()).isNotNull();
        assertThat(ejecucion.getFin()).isNotNull();
        assertThat(ejecucion.getDetalle()).isEqualTo("Cerradas 2 jornadas sin fichaje de salida.");
    }

    @Test
    @DisplayName("Un trabajo que falla queda registrado como ERROR con el motivo, y el mismo fallo llega a quien lo lanzó")
    void ejecutar_trabajoQueFalla_registraErrorYRelanzaElMismoFallo() {
        apuntarCadaGuardado();
        IllegalStateException fallo = new IllegalStateException("sin conexión con la base");

        assertThatThrownBy(() -> servicioA("2026-09-12T03:30:00").ejecutar(HORAS_EXTRA, () -> {
            throw fallo;
        })).isSameAs(fallo);

        assertThat(guardados).containsExactly(EN_CURSO, ERROR);
        ArgumentCaptor<ScheduledTaskRun> captor = ArgumentCaptor.forClass(ScheduledTaskRun.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getDetalle()).isEqualTo("IllegalStateException: sin conexión con la base");
        assertThat(captor.getValue().getFin()).isNotNull();
    }

    @Test
    @DisplayName("Si falla también el registro del error, el fallo de la tarea no se pierde: lleva el otro dentro")
    void ejecutar_siFallaTambienElRegistro_elFalloDeLaTareaNoSePierde() {
        IllegalStateException alRegistrar = new IllegalStateException("base caída");
        when(repository.save(any(ScheduledTaskRun.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0))
                .thenThrow(alRegistrar);
        RuntimeException fallo = new RuntimeException("la tarea");

        assertThatThrownBy(() -> servicioA("2026-09-12T03:00:00").ejecutar(CIERRE_JORNADAS, () -> {
            throw fallo;
        })).isSameAs(fallo);

        assertThat(fallo.getSuppressed()).containsExactly(alRegistrar);
    }

    @Test
    @DisplayName("Un resumen más largo que la columna se recorta en vez de hacer fallar el registro")
    void ejecutar_detalleLargo_seRecorta() {
        apuntarCadaGuardado();

        servicioA("2026-09-12T03:00:00").ejecutar(CIERRE_JORNADAS, () -> "x".repeat(5000));

        ArgumentCaptor<ScheduledTaskRun> captor = ArgumentCaptor.forClass(ScheduledTaskRun.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getDetalle()).hasSize(TaskMonitorServiceImpl.MAXIMO_DETALLE);
    }

    @Test
    @DisplayName("Pasada la hora, la última hora programada es la de hoy")
    void ultimaHoraProgramada_pasadaLaHora_esLaDeHoy() {
        assertThat(TaskMonitorServiceImpl.ultimaHoraProgramada(CIERRE_JORNADAS, madrid("2026-09-12T05:15:00")))
                .isEqualTo(madrid("2026-09-12T03:00:00"));
        assertThat(TaskMonitorServiceImpl.ultimaHoraProgramada(HORAS_EXTRA, madrid("2026-09-12T05:15:00")))
                .isEqualTo(madrid("2026-09-12T03:30:00"));
    }

    @Test
    @DisplayName("Antes de la hora, la última hora programada es la de ayer")
    void ultimaHoraProgramada_antesDeLaHora_esLaDeAyer() {
        assertThat(TaskMonitorServiceImpl.ultimaHoraProgramada(HORAS_EXTRA, madrid("2026-09-12T03:29:59")))
                .isEqualTo(madrid("2026-09-11T03:30:00"));
    }

    @Test
    @DisplayName("En invierno la hora programada sigue siendo las 3:00 españolas, una hora más tarde en UTC")
    void ultimaHoraProgramada_enInvierno_respetaElHorarioDeMadrid() {
        assertThat(TaskMonitorServiceImpl.ultimaHoraProgramada(CIERRE_JORNADAS, madrid("2026-12-01T04:15:00")))
                .isEqualTo(Instant.parse("2026-12-01T02:00:00Z"));
        // Y en verano, las 3:00 españolas son la 1:00 UTC.
        assertThat(TaskMonitorServiceImpl.ultimaHoraProgramada(CIERRE_JORNADAS, madrid("2026-09-12T04:15:00")))
                .isEqualTo(Instant.parse("2026-09-12T01:00:00Z"));
    }

    @Test
    @DisplayName("Con las dos tareas terminadas bien desde su última hora programada, el estado está en verde")
    void estado_ambasTareasCorrieronDesdeSuUltimaHora_ok() {
        when(repository.existsByTareaAndResultadoAndInicioGreaterThanEqual(
                CIERRE_JORNADAS, OK, madrid("2026-09-12T03:00:00"))).thenReturn(true);
        when(repository.existsByTareaAndResultadoAndInicioGreaterThanEqual(
                HORAS_EXTRA, OK, madrid("2026-09-12T03:30:00"))).thenReturn(true);

        SystemStatusResponse estado = servicioA("2026-09-12T05:15:00").estado();

        assertThat(estado.ok()).isTrue();
        assertThat(estado.tareas()).extracting(SystemStatusResponse.TaskStatus::tarea)
                .containsExactly(CIERRE_JORNADAS, HORAS_EXTRA);
    }

    @Test
    @DisplayName("Si una tarea no terminó bien desde su última hora programada, el estado global está en rojo")
    void estado_unaTareaNoCorrio_estadoEnRojo() {
        when(repository.existsByTareaAndResultadoAndInicioGreaterThanEqual(
                CIERRE_JORNADAS, OK, madrid("2026-09-12T03:00:00"))).thenReturn(true);
        when(repository.existsByTareaAndResultadoAndInicioGreaterThanEqual(
                HORAS_EXTRA, OK, madrid("2026-09-12T03:30:00"))).thenReturn(false);

        SystemStatusResponse estado = servicioA("2026-09-12T05:15:00").estado();

        assertThat(estado.ok()).isFalse();
        assertThat(estado.tareas().get(0).ok()).isTrue();
        assertThat(estado.tareas().get(1).ok()).isFalse();
    }

    @Test
    @DisplayName("Justo después de la hora programada se juzga la de ayer: la tarea puede estar corriendo todavía")
    void estado_dentroDelMargen_juzgaLaEjecucionAnterior() {
        // 3:10: la tarea de las 3:00 aún está dentro de los 15 minutos de
        // gracia, así que lo exigible es que corriera AYER a las 3:00.
        when(repository.existsByTareaAndResultadoAndInicioGreaterThanEqual(
                CIERRE_JORNADAS, OK, madrid("2026-09-11T03:00:00"))).thenReturn(true);
        when(repository.existsByTareaAndResultadoAndInicioGreaterThanEqual(
                HORAS_EXTRA, OK, madrid("2026-09-11T03:30:00"))).thenReturn(true);

        SystemStatusResponse estado = servicioA("2026-09-12T03:10:00").estado();

        assertThat(estado.ok()).isTrue();
        assertThat(estado.tareas().get(0).debioCorrer()).isEqualTo(madrid("2026-09-11T03:00:00"));
    }

    @Test
    @DisplayName("El estado de cada tarea lleva su última ejecución, cómo acabó y cuándo fue la última correcta")
    void estado_llevaUltimaEjecucionYUltimaCorrecta() {
        Instant anoche = madrid("2026-09-12T03:00:00");
        Instant anteanoche = madrid("2026-09-11T03:00:00");
        when(repository.findFirstByTareaOrderByInicioDesc(CIERRE_JORNADAS)).thenReturn(Optional.of(
                ScheduledTaskRun.builder().tarea(CIERRE_JORNADAS).inicio(anoche).resultado(ERROR).build()));
        when(repository.findFirstByTareaOrderByInicioDesc(HORAS_EXTRA)).thenReturn(Optional.empty());
        when(repository.findFirstByTareaAndResultadoOrderByInicioDesc(CIERRE_JORNADAS, OK)).thenReturn(Optional.of(
                ScheduledTaskRun.builder().tarea(CIERRE_JORNADAS).inicio(anteanoche).resultado(OK).build()));
        when(repository.findFirstByTareaAndResultadoOrderByInicioDesc(HORAS_EXTRA, OK)).thenReturn(Optional.empty());

        SystemStatusResponse estado = servicioA("2026-09-12T05:15:00").estado();

        SystemStatusResponse.TaskStatus cierre = estado.tareas().get(0);
        assertThat(cierre.ok()).isFalse();
        assertThat(cierre.ultimaEjecucion()).isEqualTo(anoche);
        assertThat(cierre.ultimoResultado()).isEqualTo(ERROR);
        assertThat(cierre.ultimaCorrecta()).isEqualTo(anteanoche);

        SystemStatusResponse.TaskStatus horasExtra = estado.tareas().get(1);
        assertThat(horasExtra.ultimaEjecucion()).isNull();
        assertThat(horasExtra.ultimaCorrecta()).isNull();
    }
}
