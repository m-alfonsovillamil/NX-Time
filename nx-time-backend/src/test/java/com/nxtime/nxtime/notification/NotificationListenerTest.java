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
import com.nxtime.nxtime.domain.CorrectionRequest;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CreateNoticeCommand;
import com.nxtime.nxtime.service.NoticeService;
import java.time.LocalDate;
import java.util.List;
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
    @DisplayName("El alta ya no manda correo desde el listener: el de bienvenida es el del código de acceso")
    void onEmployeeCreated_noMandaCorreo() {
        listener.onEmployeeCreated(new NotificationEvents.EmployeeCreated(empleado, "Empresa Test"));

        // Lo manda AccessCodeService, en el momento y dentro de la
        // transacción del alta (ADR 014). Dos correos seguidos al mismo
        // buzón habrían sido contradictorios.
        org.mockito.Mockito.verifyNoInteractions(emailSender);
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
    // ------------------------------------------------------------------
    // Los avisos que no cubría nadie
    //
    // NotificationListener era la clase con lógica peor cubierta del
    // backend (78 %), y no es una clase cualquiera: aquí vivió el fallo de
    // "el aviso solo llegaba al GESTOR". Lo que estas pruebas fijan, más
    // que los textos, es que TODOS los destinatarios del evento reciben su
    // aviso Y su correo. Ese es el defecto que se repite.
    // ------------------------------------------------------------------

    private User rrhh() {
        return User.builder().id(30L).email("rrhh@nxtime.test").nombre("Elena").empresa(empresa).build();
    }

    @Test
    @DisplayName("Trabajar en un día no laborable avisa a TODOS los destinatarios, no solo al primero")
    void onWorkedOnNonWorkingDay_avisaATodos() {
        User elena = rrhh();

        listener.onWorkedOnNonWorkingDay(new NotificationEvents.WorkedOnNonWorkingDay(
                empresa.getId(), "Ana", LocalDate.of(2026, 10, 12),
                "Festivo: Fiesta Nacional", false, List.of(gestor, elena)));

        ArgumentCaptor<CreateNoticeCommand> avisos = ArgumentCaptor.captor();
        verify(noticeService, org.mockito.Mockito.times(2)).publicar(avisos.capture());
        assertThat(avisos.getAllValues()).extracting(CreateNoticeCommand::destinatarioId)
                .containsExactly(gestor.getId(), elena.getId());
        assertThat(avisos.getAllValues()).extracting(CreateNoticeCommand::tipo)
                .containsOnly(NoticeType.TRABAJO_EN_DIA_NO_LABORABLE);

        verify(emailSender).enviar(eq(gestor.getEmail()), anyString(), eq("worked-on-non-working-day"), anyMap());
        verify(emailSender).enviar(eq(elena.getEmail()), anyString(), eq("worked-on-non-working-day"), anyMap());
    }

    @Test
    @DisplayName("Si el día era de vacaciones, el aviso lo dice: ese día no vuelve solo al saldo")
    void onWorkedOnNonWorkingDay_vacaciones_loDice() {
        listener.onWorkedOnNonWorkingDay(new NotificationEvents.WorkedOnNonWorkingDay(
                empresa.getId(), "Ana", LocalDate.of(2026, 8, 10),
                "Vacaciones aprobadas", true, List.of(gestor)));

        ArgumentCaptor<CreateNoticeCommand> aviso = ArgumentCaptor.captor();
        verify(noticeService).publicar(aviso.capture());
        // Es lo que hace accionable el aviso: hay que devolverle el día a mano.
        assertThat(aviso.getValue().cuerpo()).contains("no vuelve solo al saldo");
        // Y la fecha se lee, no viene en ISO.
        assertThat(aviso.getValue().cuerpo()).contains("10/08/2026");
    }

    @Test
    @DisplayName("Una disputa avisa a todos los que pueden resolverla, con el motivo de quien no la acepta")
    void onCorrectionDisputed_avisaAQuienPuedeResolverla() {
        User elena = rrhh();
        TimeEntry fichaje = TimeEntry.builder().id(7L).usuario(empleado).empresa(empresa).build();
        CorrectionRequest solicitud = CorrectionRequest.builder()
                .id(3L).registro(fichaje).empresa(empresa).solicitante(gestor)
                .motivo("Te faltaba media hora").motivoDisputa("Ese dia sali antes, no despues")
                .build();

        listener.onCorrectionDisputed(new NotificationEvents.CorrectionDisputed(solicitud, List.of(elena, gestor)));

        ArgumentCaptor<CreateNoticeCommand> avisos = ArgumentCaptor.captor();
        verify(noticeService, org.mockito.Mockito.times(2)).publicar(avisos.capture());
        assertThat(avisos.getAllValues()).extracting(CreateNoticeCommand::destinatarioId)
                .containsExactly(elena.getId(), gestor.getId());
        // El cuerpo es el motivo de la disputa: es lo que hay que juzgar.
        assertThat(avisos.getAllValues()).extracting(CreateNoticeCommand::cuerpo)
                .containsOnly("Ese dia sali antes, no despues");
        assertThat(avisos.getAllValues()).extracting(CreateNoticeCommand::titulo)
                .containsOnly("Ana no acepta una corrección de su fichaje");
    }

    @Test
    @DisplayName("La bolsa de horas extra se anuncia distinto a su dueño que a quien la revisa")
    void onOvertimeBalanceNearLimit_distingueAlPropio() {
        listener.onOvertimeBalanceNearLimit(new NotificationEvents.OvertimeBalanceNearLimit(
                empleado, 2026, 70 * 60, 10 * 60, List.of(empleado, gestor)));

        ArgumentCaptor<CreateNoticeCommand> avisos = ArgumentCaptor.captor();
        verify(noticeService, org.mockito.Mockito.times(2)).publicar(avisos.capture());
        assertThat(avisos.getAllValues()).extracting(CreateNoticeCommand::titulo)
                .containsExactly(
                        "Tu bolsa de horas extra se está agotando",
                        "La bolsa de horas extra de Ana se está agotando");
        // Las horas se leen, no van en minutos sueltos.
        assertThat(avisos.getAllValues()).extracting(CreateNoticeCommand::cuerpo)
                .allSatisfy(cuerpo -> assertThat(cuerpo).contains("de las 80 h del año"));

        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.captor();
        verify(emailSender, org.mockito.Mockito.times(2))
                .enviar(anyString(), anyString(), eq("overtime-balance-near-limit"), variables.capture());
        assertThat(variables.getAllValues()).extracting(v -> v.get("propio")).containsExactly(true, false);
    }

}
