package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Complaint;
import com.nxtime.nxtime.domain.ComplaintAuthor;
import com.nxtime.nxtime.domain.ComplaintCategory;
import com.nxtime.nxtime.domain.ComplaintMessage;
import com.nxtime.nxtime.domain.ComplaintStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ComplaintCreatedResponse;
import com.nxtime.nxtime.dto.ComplaintMessageRequest;
import com.nxtime.nxtime.dto.ComplaintResponse;
import com.nxtime.nxtime.dto.CreateComplaintRequest;
import com.nxtime.nxtime.dto.UpdateComplaintStatusRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.ComplaintMessageRepository;
import com.nxtime.nxtime.repository.ComplaintRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TrackingCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Unitarios del canal de denuncias (Fase G).
 *
 * <b>La mitad de este fichero prueba que algo NO se guarda</b>, que es
 * una forma rara de test y aquí es la importante: el anonimato no se ve
 * en la respuesta de un endpoint, se ve en que el id del denunciante no
 * está en la fila, ni en el mensaje, ni en la lista de avisados. Un
 * fallo ahí no rompe ninguna pantalla — solo deja de proteger a alguien,
 * en silencio, hasta que le pasa algo.
 *
 * La otra mitad prueba los plazos del art. 9.2 de la Ley 2/2023 y la
 * máquina de estados, incluida la regla de que cerrar exige conclusión.
 */
@ExtendWith(MockitoExtension.class)
class ComplaintServiceImplTest {

    @Mock
    private ComplaintRepository complaintRepository;
    @Mock
    private ComplaintMessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ComplaintServiceImpl service;

    private Company empresa;
    private Company otraEmpresa;
    private User empleado;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new ComplaintServiceImpl(
                complaintRepository, messageRepository, userRepository, eventPublisher);

        empresa = Company.builder().id(1L).nombre("TechCorp").build();
        otraEmpresa = Company.builder().id(2L).nombre("Otra").build();

        empleado = User.builder().id(10L).email("ana@nxtime.test").nombre("Ana")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build();
        admin = User.builder().id(20L).email("raul@nxtime.test").nombre("Raúl")
                .rol(Role.ADMIN).empresa(empresa).activo(true).build();

        lenient().when(complaintRepository.save(any()))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        lenient().when(messageRepository.findDeDenuncia(anyLong()))
                .thenReturn(List.of());
    }

    private Complaint denuncia(User denunciante, ComplaintStatus estado) {
        return Complaint.builder()
                .id(100L)
                .empresa(empresa)
                .codigoHash(TrackingCode.hash("el-codigo"))
                .denunciante(denunciante)
                .categoria(ComplaintCategory.ACOSO)
                .descripcion("Los hechos.")
                .estado(estado)
                .creadoEn(Instant.now().minus(3, ChronoUnit.DAYS))
                .acuseReciboEn(estado == ComplaintStatus.RECIBIDA
                        ? null : Instant.now().minus(2, ChronoUnit.DAYS))
                .resueltaEn(estado.estaAbierta() ? null : Instant.now().minus(1, ChronoUnit.DAYS))
                .conclusion(estado.estaAbierta() ? null : "Se archiva.")
                .build();
    }

    // ==================================================================
    // Presentar
    // ==================================================================

    @Nested
    @DisplayName("Presentar una denuncia")
    class Presentar {

        @Test
        @DisplayName("anónima: el id del denunciante NO se guarda, aunque el actor esté autenticado")
        void anonima_noGuardaAlDenunciante() {
            when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(empleado, admin));

            service.presentar(new CreateComplaintRequest(
                    ComplaintCategory.ACOSO, "Los hechos.", true), empleado);

            ArgumentCaptor<Complaint> guardada = ArgumentCaptor.forClass(Complaint.class);
            verify(complaintRepository).save(guardada.capture());
            assertThat(guardada.getValue().getDenunciante()).isNull();
            assertThat(guardada.getValue().esAnonima()).isTrue();
        }

        @Test
        @DisplayName("identificada: sí se guarda quién la puso")
        void identificada_guardaAlDenunciante() {
            when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(admin));

            service.presentar(new CreateComplaintRequest(
                    ComplaintCategory.FRAUDE, "Los hechos.", false), empleado);

            ArgumentCaptor<Complaint> guardada = ArgumentCaptor.forClass(Complaint.class);
            verify(complaintRepository).save(guardada.capture());
            assertThat(guardada.getValue().getDenunciante()).isEqualTo(empleado);
        }

        @Test
        @DisplayName("el código se devuelve una vez y de él solo se guarda el hash")
        void devuelveElCodigoYGuardaSoloElHash() {
            when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(admin));

            ComplaintCreatedResponse respuesta = service.presentar(new CreateComplaintRequest(
                    ComplaintCategory.SEGURIDAD, "Los hechos.", true), empleado);

            ArgumentCaptor<Complaint> guardada = ArgumentCaptor.forClass(Complaint.class);
            verify(complaintRepository).save(guardada.capture());

            assertThat(respuesta.codigoSeguimiento()).isNotBlank();
            // Lo guardado NO es el código: es su hash, y cuadra con él.
            assertThat(guardada.getValue().getCodigoHash())
                    .isNotEqualTo(respuesta.codigoSeguimiento())
                    .hasSize(64)
                    .isEqualTo(TrackingCode.hash(respuesta.codigoSeguimiento()));
        }

        @Test
        @DisplayName("avisa a quien instruye, y NO excluye al denunciante de la lista")
        void avisaATodosLosInstructoresSinExcluirANadie() {
            // Dos ADMIN, y uno de ellos es quien denuncia. Si el servicio
            // "por cortesía" no se avisara a sí mismo, el otro sabría de
            // quién es la denuncia anónima por quién falta en la lista.
            User otroAdmin = User.builder().id(21L).email("otro@nxtime.test").nombre("Otro")
                    .rol(Role.ADMIN).empresa(empresa).activo(true).build();
            when(userRepository.findByEmpresa(empresa))
                    .thenReturn(List.of(empleado, admin, otroAdmin));

            service.presentar(new CreateComplaintRequest(
                    ComplaintCategory.ACOSO, "Los hechos.", true), admin);

            ArgumentCaptor<NotificationEvents.ComplaintReceived> evento =
                    ArgumentCaptor.forClass(NotificationEvents.ComplaintReceived.class);
            verify(eventPublisher).publishEvent(evento.capture());
            assertThat(evento.getValue().destinatarios())
                    .containsExactlyInAnyOrder(admin, otroAdmin);
        }

        @Test
        @DisplayName("un empleado de baja no recibe el aviso aunque tuviera el rol")
        void noAvisaACuentasDeBaja() {
            User adminDeBaja = User.builder().id(22L).email("baja@nxtime.test").nombre("Baja")
                    .rol(Role.ADMIN).empresa(empresa).activo(false).build();
            when(userRepository.findByEmpresa(empresa)).thenReturn(List.of(admin, adminDeBaja));

            service.presentar(new CreateComplaintRequest(
                    ComplaintCategory.ACOSO, "Los hechos.", true), empleado);

            ArgumentCaptor<NotificationEvents.ComplaintReceived> evento =
                    ArgumentCaptor.forClass(NotificationEvents.ComplaintReceived.class);
            verify(eventPublisher).publishEvent(evento.capture());
            assertThat(evento.getValue().destinatarios()).containsExactly(admin);
        }
    }

    // ==================================================================
    // La puerta del código
    // ==================================================================

    @Nested
    @DisplayName("Seguimiento con el código")
    class Seguimiento {

        @Test
        @DisplayName("con el código correcto devuelve el expediente, sin mirar quién lo trae")
        void conCodigoCorrecto_devuelveElExpediente() {
            Complaint anonima = denuncia(null, ComplaintStatus.EN_INVESTIGACION);
            when(complaintRepository.findByCodigoHash(TrackingCode.hash("el-codigo")))
                    .thenReturn(Optional.of(anonima));

            // Lo consulta alguien que NO es quien denunció -- y no puede
            // serlo, porque la denuncia es anónima. El código basta.
            ComplaintResponse respuesta = service.seguimiento("el-codigo", admin);

            assertThat(respuesta.id()).isEqualTo(100L);
            assertThat(respuesta.anonima()).isTrue();
            assertThat(respuesta.denunciante()).isNull();
        }

        @Test
        @DisplayName("un código de OTRA empresa da 404, no 403: un 403 confirmaría que existe")
        void codigoDeOtraEmpresa_da404() {
            Complaint ajena = denuncia(null, ComplaintStatus.RECIBIDA);
            ajena.setEmpresa(otraEmpresa);
            when(complaintRepository.findByCodigoHash(TrackingCode.hash("el-codigo")))
                    .thenReturn(Optional.of(ajena));

            assertThatThrownBy(() -> service.seguimiento("el-codigo", empleado))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("un código que no existe da el MISMO 404")
        void codigoInexistente_da404() {
            when(complaintRepository.findByCodigoHash(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.seguimiento("inventado", empleado))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("un código vacío no llega ni a consultar la base")
        void codigoVacio_da404SinConsultar() {
            assertThatThrownBy(() -> service.seguimiento("   ", empleado))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(complaintRepository, never()).findByCodigoHash(any());
        }
    }

    // ==================================================================
    // Responder
    // ==================================================================

    @Nested
    @DisplayName("Mensajes del denunciante")
    class MensajesDelDenunciante {

        @Test
        @DisplayName("en una denuncia anónima el mensaje se guarda SIN autor, con el actor delante")
        void enAnonima_elMensajeVaSinAutor() {
            Complaint anonima = denuncia(null, ComplaintStatus.EN_INVESTIGACION);
            when(complaintRepository.findByCodigoHash(TrackingCode.hash("el-codigo")))
                    .thenReturn(Optional.of(anonima));

            service.responder("el-codigo", new ComplaintMessageRequest("Aporto fechas."), empleado);

            ArgumentCaptor<ComplaintMessage> mensaje =
                    ArgumentCaptor.forClass(ComplaintMessage.class);
            verify(messageRepository).save(mensaje.capture());
            assertThat(mensaje.getValue().getAutor()).isNull();
            assertThat(mensaje.getValue().getAutorRol()).isEqualTo(ComplaintAuthor.DENUNCIANTE);
        }

        @Test
        @DisplayName("en una identificada sí lleva autor")
        void enIdentificada_elMensajeLlevaAutor() {
            Complaint identificada = denuncia(empleado, ComplaintStatus.EN_INVESTIGACION);
            when(complaintRepository.findByCodigoHash(TrackingCode.hash("el-codigo")))
                    .thenReturn(Optional.of(identificada));

            service.responder("el-codigo", new ComplaintMessageRequest("Aporto fechas."), empleado);

            ArgumentCaptor<ComplaintMessage> mensaje =
                    ArgumentCaptor.forClass(ComplaintMessage.class);
            verify(messageRepository).save(mensaje.capture());
            assertThat(mensaje.getValue().getAutor()).isEqualTo(empleado);
        }

        @Test
        @DisplayName("un expediente cerrado ya no admite mensajes")
        void expedienteCerrado_da409() {
            Complaint cerrada = denuncia(empleado, ComplaintStatus.RESUELTA);
            when(complaintRepository.findByCodigoHash(TrackingCode.hash("el-codigo")))
                    .thenReturn(Optional.of(cerrada));

            assertThatThrownBy(() -> service.responder(
                    "el-codigo", new ComplaintMessageRequest("Otra cosa."), empleado))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    // ==================================================================
    // Instruir
    // ==================================================================

    @Nested
    @DisplayName("Instruir")
    class Instruir {

        @Test
        @DisplayName("nadie instruye una denuncia identificada que puso él mismo")
        void laPropiaIdentificada_da403() {
            Complaint suya = denuncia(admin, ComplaintStatus.RECIBIDA);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(suya));

            assertThatThrownBy(() -> service.cambiarEstado(100L,
                    new UpdateComplaintStatusRequest(ComplaintStatus.EN_INVESTIGACION, null), admin))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("sobre una ANÓNIMA no se puede aplicar esa regla, y se deja pasar")
        void laPropiaAnonima_seDejaPasar() {
            // No es un agujero: es el precio del anonimato. El sistema no
            // sabe -- ni puede saber -- que esa denuncia la puso él.
            Complaint anonima = denuncia(null, ComplaintStatus.RECIBIDA);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(anonima));

            ComplaintResponse respuesta = service.cambiarEstado(100L,
                    new UpdateComplaintStatusRequest(ComplaintStatus.EN_INVESTIGACION, null), admin);

            assertThat(respuesta.estado()).isEqualTo(ComplaintStatus.EN_INVESTIGACION);
        }

        @Test
        @DisplayName("el primer mensaje del instructor vale como acuse de recibo")
        void primerMensajeDelInstructor_fechaElAcuse() {
            Complaint sinAcuse = denuncia(null, ComplaintStatus.RECIBIDA);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(sinAcuse));

            service.responderComoInstructor(
                    100L, new ComplaintMessageRequest("¿Puedes concretar fechas?"), admin);

            assertThat(sinAcuse.getAcuseReciboEn()).isNotNull();
        }

        @Test
        @DisplayName("el acuse no se re-fecha en el segundo mensaje")
        void segundoMensaje_noPisaElAcuse() {
            Complaint conAcuse = denuncia(null, ComplaintStatus.EN_INVESTIGACION);
            Instant acuseOriginal = conAcuse.getAcuseReciboEn();
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(conAcuse));

            service.responderComoInstructor(
                    100L, new ComplaintMessageRequest("Seguimos."), admin);

            assertThat(conAcuse.getAcuseReciboEn()).isEqualTo(acuseOriginal);
        }

        @Test
        @DisplayName("el mensaje del instructor SIEMPRE lleva autor, aunque la denuncia sea anónima")
        void elInstructorNuncaEsAnonimo() {
            Complaint anonima = denuncia(null, ComplaintStatus.EN_INVESTIGACION);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(anonima));

            service.responderComoInstructor(
                    100L, new ComplaintMessageRequest("Seguimos."), admin);

            ArgumentCaptor<ComplaintMessage> mensaje =
                    ArgumentCaptor.forClass(ComplaintMessage.class);
            verify(messageRepository).save(mensaje.capture());
            assertThat(mensaje.getValue().getAutor()).isEqualTo(admin);
            assertThat(mensaje.getValue().getAutorRol()).isEqualTo(ComplaintAuthor.INSTRUCTOR);
        }

        @Test
        @DisplayName("una denuncia de otra empresa da 404 también por id")
        void deOtraEmpresa_da404() {
            Complaint ajena = denuncia(null, ComplaintStatus.RECIBIDA);
            ajena.setEmpresa(otraEmpresa);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(ajena));

            assertThatThrownBy(() -> service.detalle(100L, admin))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================================================================
    // Máquina de estados
    // ==================================================================

    @Nested
    @DisplayName("Cambios de estado")
    class Estados {

        @Test
        @DisplayName("cerrar sin conclusión da 400: la ley obliga a responder, no a dar la razón")
        void cerrarSinConclusion_da400() {
            Complaint abierta = denuncia(null, ComplaintStatus.EN_INVESTIGACION);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(abierta));

            assertThatThrownBy(() -> service.cambiarEstado(100L,
                    new UpdateComplaintStatusRequest(ComplaintStatus.ARCHIVADA, "  "), admin))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("mandar conclusión SIN cerrar da 400 (lo prohíbe el CHECK de la base)")
        void conclusionSinCerrar_da400() {
            Complaint abierta = denuncia(null, ComplaintStatus.RECIBIDA);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(abierta));

            assertThatThrownBy(() -> service.cambiarEstado(100L,
                    new UpdateComplaintStatusRequest(
                            ComplaintStatus.EN_INVESTIGACION, "Ya está."), admin))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("un expediente cerrado no se reabre")
        void yaCerrada_da409() {
            Complaint cerrada = denuncia(null, ComplaintStatus.RESUELTA);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(cerrada));

            assertThatThrownBy(() -> service.cambiarEstado(100L,
                    new UpdateComplaintStatusRequest(
                            ComplaintStatus.EN_INVESTIGACION, null), admin))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("moverla al estado que ya tenía da 409")
        void mismoEstado_da409() {
            Complaint abierta = denuncia(null, ComplaintStatus.EN_INVESTIGACION);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(abierta));

            assertThatThrownBy(() -> service.cambiarEstado(100L,
                    new UpdateComplaintStatusRequest(
                            ComplaintStatus.EN_INVESTIGACION, null), admin))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("saltar de RECIBIDA a RESUELTA fecha el acuse por el camino")
        void deRecibidaACerrada_fechaElAcuseAntes() {
            // Sin esto, ck_denuncias_acuse_antes_del_cierre pararía el
            // UPDATE: no se puede cerrar lo que nunca se acusó.
            Complaint sinAcuse = denuncia(null, ComplaintStatus.RECIBIDA);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(sinAcuse));

            service.cambiarEstado(100L, new UpdateComplaintStatusRequest(
                    ComplaintStatus.RESUELTA, "Confirmada y corregida."), admin);

            assertThat(sinAcuse.getAcuseReciboEn()).isNotNull();
            assertThat(sinAcuse.getResueltaEn()).isNotNull();
            assertThat(sinAcuse.getConclusion()).isEqualTo("Confirmada y corregida.");
        }

        @Test
        @DisplayName("a un denunciante anónimo no se le puede avisar, y no se intenta")
        void anonima_noPublicaAvisoAlDenunciante() {
            Complaint anonima = denuncia(null, ComplaintStatus.RECIBIDA);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(anonima));

            service.cambiarEstado(100L, new UpdateComplaintStatusRequest(
                    ComplaintStatus.EN_INVESTIGACION, null), admin);

            verify(eventPublisher, never())
                    .publishEvent(any(NotificationEvents.ComplaintUpdated.class));
        }

        @Test
        @DisplayName("a uno identificado sí")
        void identificada_avisaAlDenunciante() {
            Complaint identificada = denuncia(empleado, ComplaintStatus.RECIBIDA);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(identificada));

            service.cambiarEstado(100L, new UpdateComplaintStatusRequest(
                    ComplaintStatus.EN_INVESTIGACION, null), admin);

            ArgumentCaptor<NotificationEvents.ComplaintUpdated> evento =
                    ArgumentCaptor.forClass(NotificationEvents.ComplaintUpdated.class);
            verify(eventPublisher).publishEvent(evento.capture());
            assertThat(evento.getValue().destinatarios()).containsExactly(empleado);
        }
    }

    // ==================================================================
    // Plazos legales
    // ==================================================================

    @Nested
    @DisplayName("Plazos del art. 9.2")
    class Plazos {

        @Test
        @DisplayName("sin acuse a los 9 días, el plazo de 7 llega en NEGATIVO")
        void plazoDeAcuseVencido_llegaEnNegativo() {
            Complaint vieja = denuncia(null, ComplaintStatus.RECIBIDA);
            vieja.setCreadoEn(Instant.now().minus(9, ChronoUnit.DAYS));
            vieja.setAcuseReciboEn(null);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(vieja));

            ComplaintResponse respuesta = service.detalle(100L, admin);

            // Se enseña el incumplimiento, no se esconde tras un cero.
            assertThat(respuesta.diasHastaAcuse()).isEqualTo(-2L);
        }

        @Test
        @DisplayName("con el acuse dado, el plazo del acuse se apaga")
        void conAcuse_elPlazoDelAcuseEsNulo() {
            Complaint conAcuse = denuncia(null, ComplaintStatus.EN_INVESTIGACION);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(conAcuse));

            ComplaintResponse respuesta = service.detalle(100L, admin);

            assertThat(respuesta.diasHastaAcuse()).isNull();
            // El de la respuesta sigue corriendo: 3 meses desde que se
            // presentó, y se presentó hace 3 días.
            assertThat(respuesta.diasHastaRespuesta()).isNotNull().isPositive();
        }

        @Test
        @DisplayName("cerrada: el plazo de respuesta también se apaga")
        void cerrada_noTienePlazoDeRespuesta() {
            Complaint cerrada = denuncia(null, ComplaintStatus.ARCHIVADA);
            when(complaintRepository.findById(100L)).thenReturn(Optional.of(cerrada));

            ComplaintResponse respuesta = service.detalle(100L, admin);

            assertThat(respuesta.diasHastaRespuesta()).isNull();
        }
    }

    // ==================================================================
    // Listados
    // ==================================================================

    @Test
    @DisplayName("una bandeja vacía no consulta el contador de mensajes (un IN vacío no es SQL)")
    void bandejaVacia_noConsultaLosMensajes() {
        when(complaintRepository.findDeEmpresa(1L)).thenReturn(List.of());

        assertThat(service.bandeja(admin)).isEmpty();
        verify(messageRepository, never()).contarPorDenuncia(anyList());
    }

    @Test
    @DisplayName("la bandeja cuenta los mensajes de todas las filas en UNA consulta")
    void bandeja_cuentaLosMensajesDeUnaVez() {
        Complaint una = denuncia(null, ComplaintStatus.RECIBIDA);
        when(complaintRepository.findDeEmpresa(1L)).thenReturn(List.of(una));
        when(messageRepository.contarPorDenuncia(List.of(100L)))
                .thenReturn(List.<Object[]>of(new Object[]{100L, 3L}));

        assertThat(service.bandeja(admin))
                .singleElement()
                .satisfies(fila -> {
                    assertThat(fila.mensajes()).isEqualTo(3);
                    assertThat(fila.anonima()).isTrue();
                });
        verify(messageRepository).contarPorDenuncia(anyList());
    }
}
