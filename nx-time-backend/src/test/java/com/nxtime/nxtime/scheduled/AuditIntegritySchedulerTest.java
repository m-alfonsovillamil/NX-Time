package com.nxtime.nxtime.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.audit.VerificadorDeAuditoria;
import com.nxtime.nxtime.domain.ScheduledTask;
import com.nxtime.nxtime.domain.ScheduledTaskResult;
import com.nxtime.nxtime.dto.AuditIntegrityResponse;
import com.nxtime.nxtime.service.TaskMonitorService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cuándo la comprobación nocturna de la traza mira solo lo nuevo y cuándo la
 * recorre entera (ADR 042).
 *
 * El recorrido en sí se prueba contra PostgreSQL en
 * {@code VerificadorDeAuditoriaIT}; aquí, solo la decisión.
 */
@ExtendWith(MockitoExtension.class)
class AuditIntegritySchedulerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    // Octubre de 2026: el 7 es miércoles, el 11 domingo y el 12 lunes.
    private static final String UN_MIERCOLES = "2026-10-07T03:50:00";
    private static final String UN_DOMINGO = "2026-10-11T03:50:00";

    @Mock
    private VerificadorDeAuditoria verificador;
    @Mock
    private TaskMonitorService taskMonitor;
    @Mock
    private PlatformTransactionManager transactionManager;

    /** El resumen con el que la tarea le dice al monitor que ha acabado bien. */
    private final List<String> resumenes = new ArrayList<>();

    @BeforeEach
    void elMonitorDejaCorrerElTrabajo() {
        doAnswer(invocacion -> resumenes.add(invocacion.<Supplier<String>>getArgument(1).get()))
                .when(taskMonitor).ejecutar(any(), any());
    }

    private AuditIntegrityScheduler tareaA(String fechaHoraMadrid) {
        Clock reloj = Clock.fixed(LocalDateTime.parse(fechaHoraMadrid).atZone(MADRID).toInstant(), ZoneOffset.UTC);
        return new AuditIntegrityScheduler(
                verificador, taskMonitor, new TransactionTemplate(transactionManager), reloj);
    }

    private void laUltimaAcabo(ScheduledTaskResult resultado) {
        when(taskMonitor.ultimoResultado(ScheduledTask.VERIFICACION_INTEGRIDAD))
                .thenReturn(Optional.ofNullable(resultado));
    }

    @Test
    @DisplayName("Una noche cualquiera solo mira lo escrito desde el último punto de control")
    void entreSemana_soloLoNuevo() {
        laUltimaAcabo(ScheduledTaskResult.OK);
        when(verificador.verificarLoNuevoYAnotar()).thenReturn(AuditIntegrityResponse.intacta(10, 8, 2));

        tareaA(UN_MIERCOLES).verificarLaCadena();

        verify(verificador, never()).verificarTodoYAnotar();
        assertThat(resumenes).containsExactly("Cadena intacta: 10 movimientos (8 recalculados, 2 solo enlace).");
    }

    @Test
    @DisplayName("La madrugada del domingo recorre la traza entera, y lo dice en el resumen")
    void elDomingo_entera() {
        when(verificador.verificarTodoYAnotar()).thenReturn(AuditIntegrityResponse.intacta(10, 8, 2));

        tareaA(UN_DOMINGO).verificarLaCadena();

        verify(verificador, never()).verificarLoNuevoYAnotar();
        assertThat(resumenes)
                .containsExactly("Cadena intacta, recorrida entera: 10 movimientos (8 recalculados, 2 solo enlace).");
    }

    /**
     * Sin esto, un fallo encontrado el domingo en una fila vieja se apagaría
     * solo el lunes: lo incremental no vuelve a pasar por ella.
     */
    @Test
    @DisplayName("Si la última vez acabó en error, se recorre entera aunque no sea domingo")
    void trasUnError_entera() {
        laUltimaAcabo(ScheduledTaskResult.ERROR);
        when(verificador.verificarTodoYAnotar()).thenReturn(AuditIntegrityResponse.intacta(10, 8, 2));

        tareaA(UN_MIERCOLES).verificarLaCadena();

        verify(verificador, never()).verificarLoNuevoYAnotar();
    }

    @Test
    @DisplayName("La primera vez, sin ninguna ejecución anterior, no hay error que arrastrar")
    void sinEjecucionesAnteriores_soloLoNuevo() {
        laUltimaAcabo(null);
        when(verificador.verificarLoNuevoYAnotar()).thenReturn(AuditIntegrityResponse.intacta(0, 0, 0));

        tareaA(UN_MIERCOLES).verificarLaCadena();

        verify(verificador, never()).verificarTodoYAnotar();
    }

    @Test
    @DisplayName("El domingo es el de Madrid: el lunes a la una de la madrugada, que en UTC aún es domingo, no toca")
    void elDomingoEsElDeMadrid() {
        laUltimaAcabo(ScheduledTaskResult.OK);
        when(verificador.verificarLoNuevoYAnotar()).thenReturn(AuditIntegrityResponse.intacta(10, 8, 2));

        tareaA("2026-10-12T01:00:00").verificarLaCadena();

        verify(verificador, never()).verificarTodoYAnotar();
    }

    @Test
    @DisplayName("Una cadena rota hace fallar la tarea con la fila y el motivo")
    void cadenaRota_laTareaFalla() {
        when(verificador.verificarTodoYAnotar())
                .thenReturn(AuditIntegrityResponse.rota(10, 8, 2, 7L, "le han cambiado algo"));

        assertThatThrownBy(() -> tareaA(UN_DOMINGO).verificarLaCadena())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cadena de auditoría rota en el movimiento 7: le han cambiado algo");
        assertThat(resumenes).isEmpty();
    }
}
