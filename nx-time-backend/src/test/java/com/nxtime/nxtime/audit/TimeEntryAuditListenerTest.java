package com.nxtime.nxtime.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.TimeEntryAudit;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unitarios del encadenamiento de hashes de la auditoría (Fase 8): la
 * parte de {@link TimeEntryAuditListener} que ni {@link
 * com.nxtime.nxtime.service.impl.TimeEntryServiceImplTest} ni los
 * {@code @WebMvcTest} de los controladores llegan a ejercitar de
 * verdad, porque ahí el {@code ApplicationEventPublisher} está
 * mockeado -- publicar en un mock no invoca ningún listener real.
 */
@ExtendWith(MockitoExtension.class)
class TimeEntryAuditListenerTest {

    @Mock
    private TimeEntryAuditRepository auditRepository;

    private TimeEntryAuditListener listener;

    @BeforeEach
    void setUp() {
        listener = new TimeEntryAuditListener(auditRepository);
    }

    private TimeEntryAudit nuevaFilaSinGuardar() {
        User user = User.builder().id(1L).email("empleado@nxtime.test").build();
        TimeEntry entry = TimeEntry.builder().id(10L).usuario(user).build();
        return TimeEntryAudit.builder()
                .registro(entry)
                .usuario(user)
                .modificadoPor(user)
                .accion(AuditAction.CREACION)
                .valorNuevo("{\"id\":10}")
                .build();
    }

    @Test
    @DisplayName("La primera fila de la cadena no tiene hashAnterior, pero sí su propio hash")
    void onTimeEntryAudit_primeraFila_hashAnteriorNuloYHashPropioCalculado() {
        when(auditRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());

        TimeEntryAudit row = nuevaFilaSinGuardar();
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(row));

        assertThat(row.getHashAnterior()).isNull();
        assertThat(row.getHash()).isNotBlank().hasSize(64); // SHA-256 en hexadecimal: 64 caracteres.
        assertThat(row.getFechaHora()).isNotNull();
    }

    @Test
    @DisplayName("Una fila siguiente encadena con el hash de la anterior")
    void onTimeEntryAudit_filaSiguiente_encadenaConHashDeLaAnterior() {
        TimeEntryAudit anterior = TimeEntryAudit.builder().id(1L).hash("hash-de-la-fila-anterior").build();
        when(auditRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(anterior));

        TimeEntryAudit row = nuevaFilaSinGuardar();
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(row));

        assertThat(row.getHashAnterior()).isEqualTo("hash-de-la-fila-anterior");
        assertThat(row.getHash()).isNotEqualTo(anterior.getHash());
    }

    @Test
    @DisplayName("Dos filas con contenido distinto producen hashes distintos")
    void onTimeEntryAudit_contenidoDistinto_produceHashesDistintos() {
        when(auditRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());

        TimeEntryAudit fila1 = nuevaFilaSinGuardar();
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(fila1));

        TimeEntryAudit fila2 = nuevaFilaSinGuardar();
        fila2.setValorNuevo("{\"id\":10,\"horaSalida\":\"2026-01-01T17:00:00Z\"}");
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(fila2));

        assertThat(fila1.getHash()).isNotEqualTo(fila2.getHash());
    }

    @Test
    @DisplayName("Sin contexto de petición HTTP (ej. en un test) no se captura IP, pero tampoco lanza excepción")
    void onTimeEntryAudit_sinContextoDePeticion_noCapturaIpNiLanza() {
        when(auditRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());

        TimeEntryAudit row = nuevaFilaSinGuardar();
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(row));

        assertThat(row.getIp()).isNull();
    }

    @Test
    @DisplayName("La fila resultante se guarda a través del repositorio")
    void onTimeEntryAudit_guardaLaFilaAtravesDelRepositorio() {
        when(auditRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(auditRepository.save(any(TimeEntryAudit.class))).thenAnswer(inv -> inv.getArgument(0));

        TimeEntryAudit row = nuevaFilaSinGuardar();
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(row));

        ArgumentCaptor<TimeEntryAudit> captor = ArgumentCaptor.forClass(TimeEntryAudit.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(row);
    }
}
