package com.nxtime.nxtime.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CreateNoticeCommand;
import com.nxtime.nxtime.service.NoticeService;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unitarios del listener de notificaciones: qué correo se manda, a
 * quién y con qué datos (Fase 10), y qué aviso in-app se publica junto
 * a él (Fase A). Que las plantillas rendericen se comprueba en {@link
 * EmailTemplateRenderingTest}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private EmailSender emailSender;

    @Mock
    private NoticeService noticeService;

    private NotificationListener listener;

    private Company empresa;
    private User empleado;
    private User gestor;

    @BeforeEach
    void setUp() {
        listener = new NotificationListener(emailSender, noticeService);
        empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        empleado = User.builder().id(10L).email("empleado@nxtime.test").nombre("Ana").empresa(empresa).build();
        gestor = User.builder().id(20L).email("gestor@nxtime.test").nombre("Marta").empresa(empresa).build();
    }

    private AbsenceRequest peticion(AbsenceStatus estado) {
        return AbsenceRequest.builder()
                .id(5L).usuario(empleado).empresa(empresa)
                .fechaInicio(LocalDate.of(2026, 6, 1)).fechaFin(LocalDate.of(2026, 6, 5))
                .tipo(AbsenceType.VACACIONES).estado(estado)
                .build();
    }

    @Test
    @DisplayName("Una petición nueva avisa al gestor, con el nombre del empleado en el asunto")
    void onAbsenceRequested_avisaAlGestor() {
        listener.onAbsenceRequested(new NotificationEvents.AbsenceRequested(
                peticion(AbsenceStatus.PENDIENTE), gestor));

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.captor();
        verify(emailSender).enviar(
                eq(gestor.getEmail()),
                eq("Nueva petición de ausencia de Ana"),
                eq("absence-requested"),
                vars.capture());
        assertThat(vars.getValue()).containsEntry("nombreGestor", "Marta");
        assertThat(vars.getValue()).containsEntry("nombreEmpleado", "Ana");
    }

    @Test
    @DisplayName("Una aprobación avisa al EMPLEADO (no al gestor) y el asunto dice 'aprobada'")
    void onAbsenceResolved_aprobada_avisaAlEmpleado() {
        AbsenceRequest aprobada = peticion(AbsenceStatus.APROBADA);
        aprobada.setAprobadoPor(gestor);
        aprobada.setComentarioResolucion("Que las disfrutes.");

        listener.onAbsenceResolved(new NotificationEvents.AbsenceResolved(aprobada));

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.captor();
        verify(emailSender).enviar(
                eq(empleado.getEmail()),
                eq("Tu petición de ausencia ha sido aprobada"),
                eq("absence-resolved"),
                vars.capture());
        assertThat(vars.getValue()).containsEntry("aprobada", true);
        assertThat(vars.getValue()).containsEntry("resolutor", "Marta");
        assertThat(vars.getValue()).containsEntry("comentario", "Que las disfrutes.");
    }

    @Test
    @DisplayName("Un rechazo usa la misma plantilla pero con asunto y marca distintos")
    void onAbsenceResolved_rechazada_marcaComoNoAprobada() {
        AbsenceRequest rechazada = peticion(AbsenceStatus.RECHAZADA);
        rechazada.setAprobadoPor(gestor);
        rechazada.setComentarioResolucion("Coincide con el cierre trimestral.");

        listener.onAbsenceResolved(new NotificationEvents.AbsenceResolved(rechazada));

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.captor();
        verify(emailSender).enviar(
                eq(empleado.getEmail()),
                eq("Tu petición de ausencia ha sido rechazada"),
                eq("absence-resolved"),
                vars.capture());
        assertThat(vars.getValue()).containsEntry("aprobada", false);
    }

    @Test
    @DisplayName("Si la petición no tiene resolutor, el correo no revienta: pone 'un gestor'")
    void onAbsenceResolved_sinResolutor_usaTextoGenerico() {
        listener.onAbsenceResolved(new NotificationEvents.AbsenceResolved(peticion(AbsenceStatus.APROBADA)));

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.captor();
        verify(emailSender).enviar(anyString(), anyString(), anyString(), vars.capture());
        assertThat(vars.getValue()).containsEntry("resolutor", "un gestor");
    }

    @Test
    @DisplayName("El correo de bienvenida NO incluye la contraseña")
    void onEmployeeCreated_noIncluyeLaContrasena() {
        empleado.setContrasena("hash-super-secreto");

        listener.onEmployeeCreated(new NotificationEvents.EmployeeCreated(empleado, "Empresa Test"));

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.captor();
        verify(emailSender).enviar(
                eq(empleado.getEmail()), eq("Bienvenido a NX Time"), eq("employee-welcome"), vars.capture());
        assertThat(vars.getValue()).containsEntry("nombreEmpresa", "Empresa Test");
        // Ni la contraseña ni su hash viajan como variable de plantilla.
        assertThat(vars.getValue().values()).doesNotContain("hash-super-secreto");
        assertThat(vars.getValue()).doesNotContainKeys("contrasena", "password");
    }

    @Test
    @DisplayName("El listener no manda nada por su cuenta más allá del correo esperado")
    void listener_noMandaCorreosDeMas() {
        listener.onEmployeeCreated(new NotificationEvents.EmployeeCreated(empleado, "Empresa Test"));

        verify(emailSender).enviar(anyString(), anyString(), anyString(), anyMap());
        org.mockito.Mockito.verifyNoMoreInteractions(emailSender);
    }

    // ------------------------------------------------------------------
    // Fase A: el aviso dentro de la aplicación
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Cada evento publica su aviso, y a la persona que le toca")
    void cadaEventoAvisaAQuienCorresponde() {
        AbsenceRequest aprobada = peticion(AbsenceStatus.APROBADA);
        aprobada.setAprobadoPor(gestor);

        listener.onAbsenceRequested(new NotificationEvents.AbsenceRequested(
                peticion(AbsenceStatus.PENDIENTE), gestor));
        listener.onAbsenceResolved(new NotificationEvents.AbsenceResolved(aprobada));
        listener.onEmployeeCreated(new NotificationEvents.EmployeeCreated(empleado, "Empresa Test"));

        ArgumentCaptor<CreateNoticeCommand> comandos = ArgumentCaptor.captor();
        verify(noticeService, org.mockito.Mockito.times(3)).publicar(comandos.capture());

        // La petición se le avisa a quien tiene que resolverla; la
        // resolución y la bienvenida, al empleado.
        assertThat(comandos.getAllValues()).extracting(CreateNoticeCommand::tipo, CreateNoticeCommand::destinatarioId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(NoticeType.AUSENCIA_SOLICITADA, gestor.getId()),
                        org.assertj.core.groups.Tuple.tuple(NoticeType.AUSENCIA_RESUELTA, empleado.getId()),
                        org.assertj.core.groups.Tuple.tuple(NoticeType.BIENVENIDA, empleado.getId()));

        // Todos van etiquetados con la empresa, que es lo que permite
        // acotar o purgar por tenant sin un join.
        assertThat(comandos.getAllValues()).extracting(CreateNoticeCommand::empresaId)
                .containsOnly(empresa.getId());

        // Y con un destino lógico, no una ruta de Compose.
        assertThat(comandos.getAllValues()).extracting(CreateNoticeCommand::rutaDestino)
                .containsExactly("ausencias-equipo/pendientes", "ausencias", "fichar");
    }

    @Test
    @DisplayName("El cuerpo del aviso está escrito para leerse: ni enums en mayúsculas ni fechas ISO")
    void elCuerpoDelAvisoEsLegible() {
        AbsenceRequest aprobada = peticion(AbsenceStatus.APROBADA);
        aprobada.setAprobadoPor(gestor);
        aprobada.setComentarioResolucion("Que las disfrutes.");

        listener.onAbsenceResolved(new NotificationEvents.AbsenceResolved(aprobada));

        ArgumentCaptor<CreateNoticeCommand> comando = ArgumentCaptor.captor();
        verify(noticeService).publicar(comando.capture());

        assertThat(comando.getValue().cuerpo())
                .isEqualTo("Vacaciones, del 01/06/2026 al 05/06/2026. Que las disfrutes.");
        // Lo que NO debe salir: el nombre de la constante del enum y el
        // formato ISO de las fechas, que es lo que se colaba antes.
        assertThat(comando.getValue().cuerpo()).doesNotContain("VACACIONES").doesNotContain("2026-06-01");
    }

    @Test
    @DisplayName("El correo recibe el tipo ya escrito para leerse, no el nombre del enum")
    void elCorreoRecibeLaEtiquetaDelTipo() {
        listener.onAbsenceRequested(new NotificationEvents.AbsenceRequested(
                peticion(AbsenceStatus.PENDIENTE), gestor));

        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.captor();
        verify(emailSender).enviar(anyString(), anyString(), anyString(), vars.capture());
        assertThat(vars.getValue()).containsEntry("tipo", "Vacaciones");
    }

    @Test
    @DisplayName("El aviso se guarda ANTES de mandar el correo")
    void elAvisoSeGuardaAntesDeMandarElCorreo() {
        // El orden importa: el correo puede tardar segundos contra un
        // SMTP lento, y el aviso es el canal que el usuario ve dentro
        // de la aplicación.
        listener.onAbsenceRequested(new NotificationEvents.AbsenceRequested(
                peticion(AbsenceStatus.PENDIENTE), gestor));

        InOrder orden = inOrder(noticeService, emailSender);
        orden.verify(noticeService).publicar(any());
        orden.verify(emailSender).enviar(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Si falla el aviso, el correo sale igualmente")
    void siElAvisoFalla_elCorreoSaleIgual() {
        // Sin el try/catch del listener, la excepción escaparía del
        // método @Async y se llevaría por delante también el correo:
        // un fallo de base de datos dejaría al empleado sin enterarse
        // por ningún canal.
        doThrow(new RuntimeException("base de datos caída")).when(noticeService).publicar(any());

        AbsenceRequest aprobada = peticion(AbsenceStatus.APROBADA);
        aprobada.setAprobadoPor(gestor);

        listener.onAbsenceResolved(new NotificationEvents.AbsenceResolved(aprobada));

        verify(emailSender).enviar(
                eq(empleado.getEmail()),
                eq("Tu petición de ausencia ha sido aprobada"),
                eq("absence-resolved"),
                anyMap());
    }
}
