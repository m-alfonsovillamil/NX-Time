package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.ApplicationStatus;
import com.nxtime.nxtime.domain.Attachment;
import com.nxtime.nxtime.domain.AttachmentType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.JobApplication;
import com.nxtime.nxtime.domain.JobPosting;
import com.nxtime.nxtime.domain.JobPostingStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.JobApplicationRequest;
import com.nxtime.nxtime.dto.JobApplicationResponse;
import com.nxtime.nxtime.dto.JobPostingRequest;
import com.nxtime.nxtime.dto.JobPostingResponse;
import com.nxtime.nxtime.dto.UpdateApplicationStatusRequest;
import com.nxtime.nxtime.dto.UpdateJobPostingStatusRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.AttachmentRepository;
import com.nxtime.nxtime.repository.DepartmentRepository;
import com.nxtime.nxtime.repository.JobApplicationRepository;
import com.nxtime.nxtime.repository.JobPostingRepository;
import com.nxtime.nxtime.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * Unitarios de ofertas internas y candidaturas (Fase H).
 *
 * Lo que se fija aquí son las tres reglas que la fase añade y que un
 * cambio despistado rompería sin que se note en ninguna pantalla:
 *
 * <ul>
 *   <li><b>La candidatura congela el CV</b>: se guarda el adjunto
 *       concreto, y lo pone el servidor, no el cuerpo de la petición.</li>
 *   <li><b>Nadie valora la suya</b>, tenga el rol que tenga.</li>
 *   <li><b>El aviso a la plantilla sale una vez</b>: retirar y volver a
 *       publicar no vuelve a avisar a toda la empresa.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JobPostingServiceImplTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Mock
    private JobPostingRepository jobPostingRepository;
    @Mock
    private JobApplicationRepository applicationRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private JobPostingServiceImpl service;

    private Company empresa;
    private User empleado;
    private User gestor;
    private Attachment cv;

    @BeforeEach
    void setUp() {
        service = new JobPostingServiceImpl(
                jobPostingRepository, applicationRepository, attachmentRepository,
                departmentRepository, userRepository, eventPublisher);

        empresa = Company.builder().id(1L).nombre("TechCorp").build();
        empleado = User.builder().id(10L).email("ana@nxtime.test").nombre("Ana")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build();
        gestor = User.builder().id(20L).email("marta@nxtime.test").nombre("Marta")
                .rol(Role.GESTOR).empresa(empresa).activo(true).build();

        cv = Attachment.builder().id(77L).empresa(empresa).usuario(empleado)
                .tipo(AttachmentType.CV).nombreOriginal("cv-ana.pdf")
                .mime("application/pdf").tamanoBytes(2048).subidoEn(Instant.now())
                .vigente(true).build();

        lenient().when(jobPostingRepository.save(any()))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        lenient().when(applicationRepository.saveAndFlush(any()))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        lenient().when(applicationRepository.save(any()))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        lenient().when(applicationRepository.findDeOferta(anyLong())).thenReturn(List.of());
        lenient().when(applicationRepository.findByOferta_IdAndUsuario_Id(anyLong(), anyLong()))
                .thenReturn(Optional.empty());
    }

    private JobPosting oferta(JobPostingStatus estado, LocalDate fechaCierre) {
        return JobPosting.builder()
                .id(5L)
                .empresa(empresa)
                .titulo("Backend sénior")
                .descripcion("Java 21 y Spring Boot.")
                .publicadaPor(gestor)
                .estado(estado)
                .fechaPublicacion(estado == JobPostingStatus.BORRADOR ? null : Instant.now())
                .fechaCierre(fechaCierre)
                .creadoEn(Instant.now())
                .build();
    }

    private JobApplication candidatura(User candidato, ApplicationStatus estado) {
        return JobApplication.builder()
                .id(30L)
                .oferta(oferta(JobPostingStatus.ABIERTA, null))
                .usuario(candidato)
                .cv(cv)
                .estado(estado)
                .creadoEn(Instant.now())
                .build();
    }

    // ==================================================================
    // Presentarse: el CV se congela
    // ==================================================================

    @Nested
    @DisplayName("Presentar candidatura")
    class Presentar {

        @Test
        @DisplayName("guarda el ADJUNTO concreto, no una referencia al usuario")
        void congelaElAdjunto() {
            when(jobPostingRepository.findConDetalle(5L))
                    .thenReturn(Optional.of(oferta(JobPostingStatus.ABIERTA, null)));
            when(attachmentRepository.findByUsuarioAndTipoAndVigenteTrue(
                    empleado, AttachmentType.CV)).thenReturn(Optional.of(cv));

            service.presentarCandidatura(5L, new JobApplicationRequest("Me presento."), empleado);

            ArgumentCaptor<JobApplication> guardada =
                    ArgumentCaptor.forClass(JobApplication.class);
            verify(applicationRepository).saveAndFlush(guardada.capture());
            // El adjunto de ESTE momento. Si mañana sube otro CV, esta
            // candidatura sigue apuntando aquí.
            assertThat(guardada.getValue().getCv()).isSameAs(cv);
            assertThat(guardada.getValue().getEstado()).isEqualTo(ApplicationStatus.RECIBIDA);
        }

        @Test
        @DisplayName("sin CV subido no se puede optar, y lo dice con un 400")
        void sinCv_da400() {
            when(jobPostingRepository.findConDetalle(5L))
                    .thenReturn(Optional.of(oferta(JobPostingStatus.ABIERTA, null)));
            when(attachmentRepository.findByUsuarioAndTipoAndVigenteTrue(
                    empleado, AttachmentType.CV)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.presentarCandidatura(
                    5L, new JobApplicationRequest(null), empleado))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            verify(applicationRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("se coge el CV VIGENTE: uno retirado no vale aunque siga en la tabla")
        void usaSoloElVigente() {
            when(jobPostingRepository.findConDetalle(5L))
                    .thenReturn(Optional.of(oferta(JobPostingStatus.ABIERTA, null)));
            when(attachmentRepository.findByUsuarioAndTipoAndVigenteTrue(
                    empleado, AttachmentType.CV)).thenReturn(Optional.of(cv));

            service.presentarCandidatura(5L, new JobApplicationRequest(null), empleado);

            // Desde la fase H puede haber varios CV del mismo usuario en
            // la tabla -- los que congeló otra candidatura --, así que
            // preguntar sin el filtro devolvería uno cualquiera.
            verify(attachmentRepository)
                    .findByUsuarioAndTipoAndVigenteTrue(empleado, AttachmentType.CV);
        }

        @Test
        @DisplayName("a una oferta en borrador no se puede optar")
        void ofertaEnBorrador_da409() {
            when(jobPostingRepository.findConDetalle(5L))
                    .thenReturn(Optional.of(oferta(JobPostingStatus.BORRADOR, null)));

            assertThatThrownBy(() -> service.presentarCandidatura(
                    5L, new JobApplicationRequest(null), empleado))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("con el plazo vencido tampoco, y con su propio mensaje")
        void plazoVencido_da409() {
            when(jobPostingRepository.findConDetalle(5L)).thenReturn(Optional.of(
                    oferta(JobPostingStatus.ABIERTA, LocalDate.now(MADRID).minusDays(1))));

            assertThatThrownBy(() -> service.presentarCandidatura(
                    5L, new JobApplicationRequest(null), empleado))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("plazo");
        }

        @Test
        @DisplayName("el día del cierre todavía se admite: vence al día siguiente")
        void elDiaDelCierre_todaviaSePuede() {
            when(jobPostingRepository.findConDetalle(5L)).thenReturn(Optional.of(
                    oferta(JobPostingStatus.ABIERTA, LocalDate.now(MADRID))));
            when(attachmentRepository.findByUsuarioAndTipoAndVigenteTrue(
                    empleado, AttachmentType.CV)).thenReturn(Optional.of(cv));

            JobApplicationResponse respuesta = service.presentarCandidatura(
                    5L, new JobApplicationRequest(null), empleado);

            assertThat(respuesta.estado()).isEqualTo(ApplicationStatus.RECIBIDA);
        }

        @Test
        @DisplayName("avisa a quien publicó la oferta, no a todos los gestores")
        void avisaSoloAQuienPublica() {
            when(jobPostingRepository.findConDetalle(5L))
                    .thenReturn(Optional.of(oferta(JobPostingStatus.ABIERTA, null)));
            when(attachmentRepository.findByUsuarioAndTipoAndVigenteTrue(
                    empleado, AttachmentType.CV)).thenReturn(Optional.of(cv));

            service.presentarCandidatura(5L, new JobApplicationRequest(null), empleado);

            ArgumentCaptor<NotificationEvents.JobApplicationReceived> evento =
                    ArgumentCaptor.forClass(NotificationEvents.JobApplicationReceived.class);
            verify(eventPublisher).publishEvent(evento.capture());
            assertThat(evento.getValue().destinatarios()).containsExactly(gestor);
            // Ni una consulta a la plantilla: la vacante es de alguien.
            verify(userRepository, never()).findByEmpresa(any());
        }
    }

    // ==================================================================
    // Valorar: nadie la suya
    // ==================================================================

    @Nested
    @DisplayName("Valorar candidaturas")
    class Valorar {

        @Test
        @DisplayName("un GESTOR no valora la candidatura que presentó él mismo")
        void laPropia_da403() {
            // Un gestor puede optar a una vacante como cualquiera; lo que
            // no puede es decidir sobre lo suyo. No lo para un
            // @PreAuthorize: tiene la authority.
            when(applicationRepository.findById(30L))
                    .thenReturn(Optional.of(candidatura(gestor, ApplicationStatus.RECIBIDA)));

            assertThatThrownBy(() -> service.valorar(30L,
                    new UpdateApplicationStatusRequest(ApplicationStatus.SELECCIONADA, null),
                    gestor))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("un EMPLEADO tampoco valora la de otro, aunque llegue por aquí")
        void sinAuthority_da403() {
            when(applicationRepository.findById(30L))
                    .thenReturn(Optional.of(candidatura(gestor, ApplicationStatus.RECIBIDA)));

            assertThatThrownBy(() -> service.valorar(30L,
                    new UpdateApplicationStatusRequest(ApplicationStatus.EN_PROCESO, null),
                    empleado))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("descartar sin comentario da 400: lo lee un compañero sobre sí mismo")
        void descartarSinComentario_da400() {
            when(applicationRepository.findById(30L))
                    .thenReturn(Optional.of(candidatura(empleado, ApplicationStatus.RECIBIDA)));

            assertThatThrownBy(() -> service.valorar(30L,
                    new UpdateApplicationStatusRequest(ApplicationStatus.DESCARTADA, "  "),
                    gestor))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("seleccionar no exige comentario")
        void seleccionarSinComentario_vale() {
            JobApplication suya = candidatura(empleado, ApplicationStatus.EN_PROCESO);
            when(applicationRepository.findById(30L)).thenReturn(Optional.of(suya));

            service.valorar(30L,
                    new UpdateApplicationStatusRequest(ApplicationStatus.SELECCIONADA, null),
                    gestor);

            assertThat(suya.getEstado()).isEqualTo(ApplicationStatus.SELECCIONADA);
            assertThat(suya.getResueltaPor()).isEqualTo(gestor);
            assertThat(suya.getFechaResolucion()).isNotNull();
        }

        @Test
        @DisplayName("una candidatura ya resuelta no se vuelve a mover")
        void yaResuelta_da409() {
            when(applicationRepository.findById(30L))
                    .thenReturn(Optional.of(candidatura(empleado, ApplicationStatus.DESCARTADA)));

            assertThatThrownBy(() -> service.valorar(30L,
                    new UpdateApplicationStatusRequest(ApplicationStatus.EN_PROCESO, null),
                    gestor))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("valorar avisa al candidato")
        void avisaAlCandidato() {
            when(applicationRepository.findById(30L))
                    .thenReturn(Optional.of(candidatura(empleado, ApplicationStatus.RECIBIDA)));

            service.valorar(30L,
                    new UpdateApplicationStatusRequest(ApplicationStatus.EN_PROCESO, null),
                    gestor);

            ArgumentCaptor<NotificationEvents.JobApplicationUpdated> evento =
                    ArgumentCaptor.forClass(NotificationEvents.JobApplicationUpdated.class);
            verify(eventPublisher).publishEvent(evento.capture());
            assertThat(evento.getValue().destinatarios()).containsExactly(empleado);
        }

        @Test
        @DisplayName("'puedoValorar' llega resuelto: falso sobre la propia, cierto sobre la ajena")
        void puedoValorarViajaResuelto() {
            when(applicationRepository.findMias(gestor.getId())).thenReturn(List.of(
                    candidatura(gestor, ApplicationStatus.RECIBIDA)));

            assertThat(service.misCandidaturas(gestor))
                    .singleElement()
                    .extracting(JobApplicationResponse::puedoValorar)
                    .isEqualTo(false);
        }
    }

    // ==================================================================
    // Publicar
    // ==================================================================

    @Nested
    @DisplayName("Publicar ofertas")
    class Publicar {

        @Test
        @DisplayName("una oferta nace en BORRADOR, aunque nadie lo pida")
        void naceEnBorrador() {
            service.crear(new JobPostingRequest(
                    "Backend sénior", "Java 21.", null, null, null), gestor);

            ArgumentCaptor<JobPosting> guardada = ArgumentCaptor.forClass(JobPosting.class);
            verify(jobPostingRepository).save(guardada.capture());
            // Publicar avisa a toda la plantilla: no puede ser el efecto
            // colateral de guardar un formulario a medio escribir.
            assertThat(guardada.getValue().getEstado()).isEqualTo(JobPostingStatus.BORRADOR);
            assertThat(guardada.getValue().getFechaPublicacion()).isNull();
            verify(eventPublisher, never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("publicarla avisa a la plantilla y excluye a quien publica")
        void publicar_avisaALaPlantilla() {
            when(jobPostingRepository.findConDetalle(5L))
                    .thenReturn(Optional.of(oferta(JobPostingStatus.BORRADOR, null)));
            when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(empleado, gestor));

            service.cambiarEstado(5L,
                    new UpdateJobPostingStatusRequest(JobPostingStatus.ABIERTA), gestor);

            ArgumentCaptor<NotificationEvents.JobPostingPublished> evento =
                    ArgumentCaptor.forClass(NotificationEvents.JobPostingPublished.class);
            verify(eventPublisher).publishEvent(evento.capture());
            // Aquí SÍ se excluye al autor, al revés que en las denuncias:
            // quién publica una oferta es público.
            assertThat(evento.getValue().destinatarios()).containsExactly(empleado);
        }

        @Test
        @DisplayName("retirar y volver a publicar NO vuelve a avisar a toda la empresa")
        void republicar_noVuelveAAvisar() {
            // Ya tiene fecha de publicación: estuvo publicada antes.
            JobPosting yaPublicadaAntes = oferta(JobPostingStatus.BORRADOR, null);
            yaPublicadaAntes.setFechaPublicacion(Instant.now().minusSeconds(86_400));
            when(jobPostingRepository.findConDetalle(5L))
                    .thenReturn(Optional.of(yaPublicadaAntes));

            service.cambiarEstado(5L,
                    new UpdateJobPostingStatusRequest(JobPostingStatus.ABIERTA), gestor);

            verify(eventPublisher, never())
                    .publishEvent(any(NotificationEvents.JobPostingPublished.class));
            verify(userRepository, never()).findByEmpresa(any());
        }

        @Test
        @DisplayName("una oferta cerrada no se reabre")
        void cerrada_noSeReabre() {
            when(jobPostingRepository.findConDetalle(5L))
                    .thenReturn(Optional.of(oferta(JobPostingStatus.CERRADA, null)));

            assertThatThrownBy(() -> service.cambiarEstado(5L,
                    new UpdateJobPostingStatusRequest(JobPostingStatus.ABIERTA), gestor))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("no se reabre");
        }

        @Test
        @DisplayName("crear con una fecha de cierre pasada da 400")
        void fechaDeCierrePasada_da400() {
            assertThatThrownBy(() -> service.crear(new JobPostingRequest(
                    "Backend", "Java.", null, null, LocalDate.now(MADRID).minusDays(1)), gestor))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("un borrador no existe para quien no puede publicar")
        void borradorAjeno_da404() {
            when(jobPostingRepository.findConDetalle(5L))
                    .thenReturn(Optional.of(oferta(JobPostingStatus.BORRADOR, null)));

            assertThatThrownBy(() -> service.detalle(5L, empleado))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================================================================
    // Listados
    // ==================================================================

    @Test
    @DisplayName("un empleado no ve cuántas candidaturas tiene una oferta")
    void elContadorDeCandidaturasEsSoloParaQuienValora() {
        when(jobPostingRepository.findPublicadas(1L))
                .thenReturn(List.of(oferta(JobPostingStatus.ABIERTA, null)));
        when(applicationRepository.findMias(empleado.getId())).thenReturn(List.of());

        assertThat(service.publicadas(empleado))
                .singleElement()
                .satisfies(fila -> {
                    // Cuántos compañeros han optado a un puesto no es
                    // dato para el resto de la plantilla.
                    assertThat(fila.candidaturas()).isNull();
                    assertThat(fila.admiteCandidaturas()).isTrue();
                    assertThat(fila.yaMePresente()).isFalse();
                });
        verify(applicationRepository, never()).contarPorOferta(any());
    }

    @Test
    @DisplayName("quien valora sí, y las cuenta todas en UNA consulta")
    void quienValoraVeElContador() {
        when(jobPostingRepository.findPublicadas(1L))
                .thenReturn(List.of(oferta(JobPostingStatus.ABIERTA, null)));
        when(applicationRepository.contarPorOferta(List.of(5L)))
                .thenReturn(List.<Object[]>of(new Object[]{5L, 3L}));
        when(applicationRepository.findMias(gestor.getId())).thenReturn(List.of());

        assertThat(service.publicadas(gestor))
                .singleElement()
                .extracting(JobPostingResponse::candidaturas)
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("'yaMePresente' sale de UNA consulta, no de una por oferta")
    void yaMePresenteSinNMasUno() {
        when(jobPostingRepository.findPublicadas(1L))
                .thenReturn(List.of(oferta(JobPostingStatus.ABIERTA, null)));
        when(applicationRepository.findMias(empleado.getId()))
                .thenReturn(List.of(candidatura(empleado, ApplicationStatus.RECIBIDA)));

        assertThat(service.publicadas(empleado))
                .singleElement()
                .extracting(JobPostingResponse::yaMePresente)
                .isEqualTo(true);
        verify(applicationRepository, never())
                .findByOferta_IdAndUsuario_Id(anyLong(), anyLong());
    }

    @Test
    @DisplayName("una lista vacía no consulta los contadores (un IN vacío no es SQL)")
    void listaVacia_noConsultaContadores() {
        when(jobPostingRepository.findPublicadas(1L)).thenReturn(List.of());

        assertThat(service.publicadas(gestor)).isEmpty();
        verify(applicationRepository, never()).contarPorOferta(any());
        verify(applicationRepository, never()).findMias(anyLong());
    }
}
