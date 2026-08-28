package com.nxtime.nxtime.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.User;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unitarios del listener de notificaciones (Fase 10): qué correo se
 * manda, a quién y con qué datos. Que las plantillas rendericen se
 * comprueba en {@link EmailTemplateRenderingTest}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private EmailSender emailSender;

    private NotificationListener listener;

    private User empleado;
    private User gestor;

    @BeforeEach
    void setUp() {
        listener = new NotificationListener(emailSender);
        Company empresa = Company.builder().id(1L).nombre("Empresa Test").build();
        empleado = User.builder().id(10L).email("empleado@nxtime.test").nombre("Ana").empresa(empresa).build();
        gestor = User.builder().id(20L).email("gestor@nxtime.test").nombre("Marta").empresa(empresa).build();
    }

    private AbsenceRequest peticion(AbsenceStatus estado) {
        return AbsenceRequest.builder()
                .id(5L).usuario(empleado)
                .fechaInicio(LocalDate.of(2026, 6, 1)).fechaFin(LocalDate.of(2026, 6, 5))
                .tipo(AbsenceType.VACACIONES).estado(estado)
                .build();
    }

    @Test
    @DisplayName("Una petición nueva avisa al gestor, con el nombre del empleado en el asunto")
    void onAbsenceRequested_avisaAlGestor() {
        listener.onAbsenceRequested(new NotificationEvents.AbsenceRequested(
                peticion(AbsenceStatus.PENDIENTE), gestor.getEmail(), gestor.getNombre()));

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
}
