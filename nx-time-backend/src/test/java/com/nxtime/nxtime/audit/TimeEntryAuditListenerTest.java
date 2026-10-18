package com.nxtime.nxtime.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.AuditAction;
import com.nxtime.nxtime.domain.Company;
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
import org.mockito.InOrder;
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
        // La huella va de verdad y no simulada: lo que este test comprueba es
        // el encadenado, y con un mock devolviendo siempre lo mismo no se
        // comprobaria nada.
        listener = new TimeEntryAuditListener(auditRepository, new HuellaDeAuditoria());
    }

    private TimeEntryAudit nuevaFilaSinGuardar() {
        Company empresa = Company.builder().id(7L).nombre("Empresa").build();
        User user = User.builder().id(1L).email("empleado@nxtime.test").empresa(empresa).build();
        TimeEntry entry = TimeEntry.builder().id(10L).usuario(user).empresa(empresa).build();
        return TimeEntryAudit.builder()
                .registro(entry)
                .usuario(user)
                .modificadoPor(user)
                .accion(AuditAction.CREACION)
                .valorNuevo("{\"id\":10}")
                .build();
    }

    @Test
    @DisplayName("La primera fila de la cadena de una empresa enlaza con su génesis, y lleva su empresa (ADR 050)")
    void onTimeEntryAudit_primeraFila_enlazaConLaGenesis() {
        when(auditRepository.ultimoHashDeLaCadena(7L, HuellaDeAuditoria.VERSION_POR_EMPRESA)).thenReturn(Optional.empty());

        TimeEntryAudit row = nuevaFilaSinGuardar();
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(row));

        assertThat(row.getHashAnterior()).isEqualTo(HuellaDeAuditoria.genesis(7L));
        assertThat(row.getEmpresaId()).isEqualTo(7L);
        assertThat(row.getVersionHash()).isEqualTo(HuellaDeAuditoria.VERSION_POR_EMPRESA);
        assertThat(row.getHash()).isNotBlank().hasSize(64); // SHA-256 en hexadecimal: 64 caracteres.
        assertThat(row.getFechaHora()).isNotNull();
    }

    @Test
    @DisplayName("Una fila siguiente encadena con el hash de la anterior")
    void onTimeEntryAudit_filaSiguiente_encadenaConHashDeLaAnterior() {
        when(auditRepository.ultimoHashDeLaCadena(7L, HuellaDeAuditoria.VERSION_POR_EMPRESA))
                .thenReturn(Optional.of("hash-de-la-fila-anterior"));

        TimeEntryAudit row = nuevaFilaSinGuardar();
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(row));

        assertThat(row.getHashAnterior()).isEqualTo("hash-de-la-fila-anterior");
        assertThat(row.getHash()).isNotEqualTo("hash-de-la-fila-anterior");
    }

    @Test
    @DisplayName("Dos filas con contenido distinto producen hashes distintos")
    void onTimeEntryAudit_contenidoDistinto_produceHashesDistintos() {
        when(auditRepository.ultimoHashDeLaCadena(7L, HuellaDeAuditoria.VERSION_POR_EMPRESA)).thenReturn(Optional.empty());

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
        when(auditRepository.ultimoHashDeLaCadena(7L, HuellaDeAuditoria.VERSION_POR_EMPRESA)).thenReturn(Optional.empty());

        TimeEntryAudit row = nuevaFilaSinGuardar();
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(row));

        assertThat(row.getIp()).isNull();
    }

    @Test
    @DisplayName("Se pide el lock de la cadena de la empresa ANTES de leer su última fila")
    void onTimeEntryAudit_pideElLockAntesDeLeerLaUltimaFila() {
        when(auditRepository.ultimoHashDeLaCadena(7L, HuellaDeAuditoria.VERSION_POR_EMPRESA)).thenReturn(Optional.empty());

        listener.onTimeEntryAudit(new TimeEntryAuditEvent(nuevaFilaSinGuardar()));

        // El orden es lo único que importa aquí: pedir el lock después de leer
        // no serializa nada, porque la lectura que se quiere proteger ya se
        // habría hecho. Ver el comentario de clase del listener.
        InOrder enOrden = inOrder(auditRepository);
        enOrden.verify(auditRepository).bloquearCadenaDeEmpresa(HuellaDeAuditoria.CLAVE_DEL_LOCK_DE_EMPRESA, 7);
        enOrden.verify(auditRepository).ultimoHashDeLaCadena(7L, HuellaDeAuditoria.VERSION_POR_EMPRESA);
        enOrden.verify(auditRepository).save(any(TimeEntryAudit.class));
    }

    @Test
    @DisplayName("La fila resultante se guarda a través del repositorio")
    void onTimeEntryAudit_guardaLaFilaAtravesDelRepositorio() {
        when(auditRepository.ultimoHashDeLaCadena(7L, HuellaDeAuditoria.VERSION_POR_EMPRESA)).thenReturn(Optional.empty());
        when(auditRepository.save(any(TimeEntryAudit.class))).thenAnswer(inv -> inv.getArgument(0));

        TimeEntryAudit row = nuevaFilaSinGuardar();
        listener.onTimeEntryAudit(new TimeEntryAuditEvent(row));

        ArgumentCaptor<TimeEntryAudit> captor = ArgumentCaptor.forClass(TimeEntryAudit.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(row);
    }
}
