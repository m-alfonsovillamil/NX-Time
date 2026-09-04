package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Notice;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CreateNoticeCommand;
import com.nxtime.nxtime.dto.NoticeResponse;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.NoticeRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unitarios de los avisos in-app (Fase A).
 *
 * El caso que más importa aquí no es el camino feliz sino el de
 * propiedad: un aviso es de una persona, no de una empresa, así que la
 * comprobación es más estricta que el aislamiento multi-tenant habitual
 * del proyecto.
 */
@ExtendWith(MockitoExtension.class)
class NoticeServiceImplTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CompanyRepository companyRepository;

    private NoticeServiceImpl service;

    private Company empresa;
    private User empleado;
    private User companero;

    @BeforeEach
    void setUp() {
        service = new NoticeServiceImpl(noticeRepository, userRepository, companyRepository);
        empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        empleado = User.builder().id(10L).email("empleado@nxtime.test").nombre("Ana")
                .rol(Role.EMPLEADO).empresa(empresa).build();
        companero = User.builder().id(11L).email("companero@nxtime.test").nombre("Luis")
                .rol(Role.EMPLEADO).empresa(empresa).build();
    }

    private Notice aviso(long id, User destinatario, boolean leido) {
        return Notice.builder()
                .id(id).empresa(empresa).destinatario(destinatario)
                .tipo(NoticeType.AUSENCIA_RESUELTA).titulo("Título").cuerpo("Cuerpo")
                .rutaDestino("ausencias").leido(leido).creadoEn(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Publicar guarda el aviso sin leer y con su fecha de creación")
    void publicar_guardaSinLeerYConFecha() {
        when(userRepository.getReferenceById(10L)).thenReturn(empleado);
        when(companyRepository.getReferenceById(1L)).thenReturn(empresa);

        service.publicar(new CreateNoticeCommand(
                1L, 10L, NoticeType.AUSENCIA_RESUELTA, "Tu ausencia ha sido aprobada",
                "VACACIONES, del 2026-06-01 al 2026-06-05", "ausencias"));

        ArgumentCaptor<Notice> guardado = ArgumentCaptor.captor();
        verify(noticeRepository).save(guardado.capture());

        assertThat(guardado.getValue().isLeido()).isFalse();
        assertThat(guardado.getValue().getCreadoEn()).isNotNull();
        assertThat(guardado.getValue().getDestinatario()).isEqualTo(empleado);
        assertThat(guardado.getValue().getEmpresa()).isEqualTo(empresa);
        assertThat(guardado.getValue().getRutaDestino()).isEqualTo("ausencias");
    }

    @Test
    @DisplayName("Publicar resuelve las referencias por id, sin cargar el usuario ni la empresa enteros")
    void publicar_usaReferenciasYNoConsultas() {
        when(userRepository.getReferenceById(10L)).thenReturn(empleado);
        when(companyRepository.getReferenceById(1L)).thenReturn(empresa);

        service.publicar(new CreateNoticeCommand(
                1L, 10L, NoticeType.BIENVENIDA, "Bienvenido", "Tu cuenta ya está activa.", "fichar"));

        // Corre en un hilo async sin sesión JPA: findById traería dos
        // filas que nadie necesita para escribir dos claves ajenas.
        verify(userRepository, never()).findById(any());
        verify(companyRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Mis avisos son los míos, y los devuelve con todos sus campos")
    void getMisAvisos_devuelveLosDelDestinatario() {
        when(noticeRepository.findTop50ByDestinatarioOrderByCreadoEnDesc(empleado))
                .thenReturn(List.of(aviso(1L, empleado, false), aviso(2L, empleado, true)));

        List<NoticeResponse> avisos = service.getMisAvisos(empleado);

        assertThat(avisos).hasSize(2);
        assertThat(avisos).extracting(NoticeResponse::id).containsExactly(1L, 2L);
        assertThat(avisos).extracting(NoticeResponse::leido).containsExactly(false, true);
        assertThat(avisos.get(0).tipo()).isEqualTo(NoticeType.AUSENCIA_RESUELTA);
        assertThat(avisos.get(0).rutaDestino()).isEqualTo("ausencias");
    }

    @Test
    @DisplayName("El contador de no leídos delega en la consulta del índice parcial")
    void contarNoLeidos_delegaEnElRepositorio() {
        when(noticeRepository.countByDestinatarioAndLeidoFalse(empleado)).thenReturn(3L);

        assertThat(service.contarNoLeidos(empleado)).isEqualTo(3L);
    }

    @Test
    @DisplayName("Marcar un aviso propio lo deja leído")
    void marcarLeido_avisoPropio_loMarca() {
        Notice sinLeer = aviso(1L, empleado, false);
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(sinLeer));

        service.marcarLeido(1L, empleado);

        assertThat(sinLeer.isLeido()).isTrue();
        verify(noticeRepository).save(sinLeer);
    }

    @Test
    @DisplayName("Marcar dos veces no es un error: es un usuario que ha tocado dos veces")
    void marcarLeido_yaLeido_noFallaNiVuelveAGuardar() {
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(aviso(1L, empleado, true)));

        service.marcarLeido(1L, empleado);

        verify(noticeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Marcar el aviso de un COMPAÑERO DE LA MISMA EMPRESA está prohibido")
    void marcarLeido_avisoDeOtraPersona_lanzaTenantAccess() {
        // No basta con compartir empresa: un aviso es de una persona.
        // Es lo que hace esta comprobación distinta del aislamiento
        // multi-tenant del resto del proyecto.
        when(noticeRepository.findById(9L)).thenReturn(Optional.of(aviso(9L, companero, false)));

        assertThatThrownBy(() -> service.marcarLeido(9L, empleado))
                .isInstanceOf(TenantAccessException.class)
                .hasMessageContaining("otra persona");

        verify(noticeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Marcar un aviso que no existe da 404")
    void marcarLeido_avisoInexistente_lanzaResourceNotFound() {
        when(noticeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarLeido(404L, empleado))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Marcar todos toca solo los que estaban sin leer, y dice cuántos eran")
    void marcarTodosLeidos_soloLosPendientes() {
        Notice uno = aviso(1L, empleado, false);
        Notice dos = aviso(2L, empleado, false);
        when(noticeRepository.findByDestinatarioAndLeidoFalse(empleado)).thenReturn(List.of(uno, dos));

        int marcados = service.marcarTodosLeidos(empleado);

        assertThat(marcados).isEqualTo(2);
        assertThat(uno.isLeido()).isTrue();
        assertThat(dos.isLeido()).isTrue();
        verify(noticeRepository).saveAll(List.of(uno, dos));
    }

    @Test
    @DisplayName("Marcar todos sin nada pendiente devuelve cero")
    void marcarTodosLeidos_sinPendientes_devuelveCero() {
        when(noticeRepository.findByDestinatarioAndLeidoFalse(empleado)).thenReturn(List.of());

        assertThat(service.marcarTodosLeidos(empleado)).isZero();
    }
}
