package com.nxtime.nxtime.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.service.MonthlySignatureService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lo que el listener decide antes de preguntar al servicio (Fase B3). Que la
 * invalidación ocurre de verdad, en la transacción de la corrección, lo prueba
 * FirmaMensualIT.
 */
class SignatureInvalidationListenerTest {

    private final MonthlySignatureService servicio = mock(MonthlySignatureService.class);
    private final SignatureInvalidationListener listener =
            new SignatureInvalidationListener(servicio, new ObjectMapper());

    private static final Instant ENTRADA = Instant.parse("2025-03-31T07:00:00Z");

    private static TimeEntryAudit fila(AuditAction accion) {
        return TimeEntryAudit.builder()
                .registro(TimeEntry.builder().id(1L).horaEntrada(ENTRADA).build())
                .accion(accion)
                .build();
    }

    @Test
    @DisplayName("La hora anterior se lee en ISO y como número; lo ilegible no rompe nada")
    void entradaAnterior() {
        assertThat(listener.entradaAnterior("{\"horaEntrada\":\"2025-03-31T07:00:00Z\"}")).isEqualTo(ENTRADA);
        assertThat(listener.entradaAnterior("{\"horaEntrada\":1743404400.500000000}"))
                .isEqualTo(ENTRADA.plusMillis(500));
        assertThat(listener.entradaAnterior("{\"horaEntrada\":null}")).isNull();
        assertThat(listener.entradaAnterior("no es json")).isNull();
        assertThat(listener.entradaAnterior(null)).isNull();
    }

    @Test
    @DisplayName("Pedir, rechazar o disputar una corrección, o cambiar el proyecto, ni siquiera se mira")
    void accionesQueNoCambianLoFirmado() {
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(fila(AuditAction.SOLICITUD_CORRECCION)));
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(fila(AuditAction.PROYECTO_CAMBIADO)));

        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("Una corrección se revisa, con un motivo que dice el día y no el texto de quien corrigió")
    void correccion_seRevisa() {
        TimeEntryAudit correccion = fila(AuditAction.CORRECCION);
        correccion.setMotivo("Texto libre que no debe acabar en la firma");

        listener.onTimeEntryAudit(new TimeEntryAuditEvent(correccion));

        verify(servicio).revisarTrasCambio(any(), any(), org.mockito.ArgumentMatchers.eq(
                "Se corrigió el fichaje del 31/03/2025."));
    }
}
