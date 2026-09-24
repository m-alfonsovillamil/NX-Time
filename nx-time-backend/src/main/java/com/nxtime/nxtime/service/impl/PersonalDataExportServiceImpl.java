package com.nxtime.nxtime.service.impl;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PersonalDataExport;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.AddedPauseRepository;
import com.nxtime.nxtime.repository.AttachmentRepository;
import com.nxtime.nxtime.repository.ComplaintRepository;
import com.nxtime.nxtime.repository.CorrectionRequestRepository;
import com.nxtime.nxtime.repository.JobApplicationRepository;
import com.nxtime.nxtime.repository.NoticeRepository;
import com.nxtime.nxtime.repository.OvertimeAlertRepository;
import com.nxtime.nxtime.repository.ProjectAssignmentRepository;
import com.nxtime.nxtime.repository.ScheduleAssignmentRepository;
import com.nxtime.nxtime.repository.ScheduleExceptionRepository;
import com.nxtime.nxtime.repository.ScheduleIncidentRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.repository.VacationBalanceRepository;
import com.nxtime.nxtime.service.PersonalDataExportService;
import com.nxtime.nxtime.service.ReglasDeCuadrante;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver {@link PersonalDataExportService}.
 *
 * Todo sale de los repositorios con consultas <b>sin paginar</b>: el historial
 * de la app enseña los últimos 200 fichajes y la campana los últimos 50
 * avisos, pero una exportación que se cortara ahí incumpliría justo lo que
 * existe para cumplir.
 */
@Service
@Transactional(readOnly = true)
public class PersonalDataExportServiceImpl implements PersonalDataExportService {

    private static final Logger log = LoggerFactory.getLogger(PersonalDataExportServiceImpl.class);

    /**
     * Solo es verdad en el JSON: el PDF pinta las horas en hora de España y la
     * sustituye (ver PersonalDataPdfGenerator). Es pública por eso.
     */
    public static final String NOTA_HORAS_EN_UTC = "Las horas son instantes en UTC (formato ISO 8601).";

    /** Lo que no va en el fichero, dicho dentro del propio fichero. */
    static final List<String> NOTAS = List.of(
            "La contraseña no se incluye, ni siquiera cifrada.",
            "De los ficheros adjuntos (CV y foto) se incluyen los datos, no el contenido: "
                    + "se descargan aparte desde el perfil.",
            "De las denuncias solo se incluyen las presentadas identificándote. Las anónimas no están "
                    + "vinculadas a tu cuenta, y los mensajes de cada una se consultan en el canal.",
            "Los fichajes anulados son versiones que una corrección sustituyó; se incluyen porque "
                    + "siguen formando parte del registro horario.",
            NOTA_HORAS_EN_UTC);

    private final UserRepository userRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final AddedPauseRepository addedPauseRepository;
    private final AbsenceRequestRepository absenceRepository;
    private final VacationBalanceRepository vacationRepository;
    private final CorrectionRequestRepository correctionRepository;
    private final OvertimeAlertRepository overtimeRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final ScheduleAssignmentRepository scheduleAssignmentRepository;
    private final ScheduleExceptionRepository scheduleExceptionRepository;
    private final ScheduleIncidentRepository scheduleIncidentRepository;
    private final NoticeRepository noticeRepository;
    private final AttachmentRepository attachmentRepository;
    private final JobApplicationRepository applicationRepository;
    private final ComplaintRepository complaintRepository;
    private final Clock clock;

    @Autowired
    public PersonalDataExportServiceImpl(
            UserRepository userRepository,
            TimeEntryRepository timeEntryRepository,
            AddedPauseRepository addedPauseRepository,
            AbsenceRequestRepository absenceRepository,
            VacationBalanceRepository vacationRepository,
            CorrectionRequestRepository correctionRepository,
            OvertimeAlertRepository overtimeRepository,
            ProjectAssignmentRepository assignmentRepository,
            ScheduleAssignmentRepository scheduleAssignmentRepository,
            ScheduleExceptionRepository scheduleExceptionRepository,
            ScheduleIncidentRepository scheduleIncidentRepository,
            NoticeRepository noticeRepository,
            AttachmentRepository attachmentRepository,
            JobApplicationRepository applicationRepository,
            ComplaintRepository complaintRepository) {
        this.userRepository = userRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.addedPauseRepository = addedPauseRepository;
        this.absenceRepository = absenceRepository;
        this.vacationRepository = vacationRepository;
        this.correctionRepository = correctionRepository;
        this.overtimeRepository = overtimeRepository;
        this.assignmentRepository = assignmentRepository;
        this.scheduleAssignmentRepository = scheduleAssignmentRepository;
        this.scheduleExceptionRepository = scheduleExceptionRepository;
        this.scheduleIncidentRepository = scheduleIncidentRepository;
        this.noticeRepository = noticeRepository;
        this.attachmentRepository = attachmentRepository;
        this.applicationRepository = applicationRepository;
        this.complaintRepository = complaintRepository;
        this.clock = Clock.systemUTC();
    }

    @Override
    public PersonalDataExport exportar(User actor) {
        // Se relee: el User que llega del token puede estar desfasado respecto
        // a la base (un cambio de puesto o de departamento hecho hace un rato).
        User p = userRepository.findById(actor.getId()).orElse(actor);
        long id = p.getId();

        PersonalDataExport exportacion = new PersonalDataExport(
                clock.instant(),
                new PersonalDataExport.Persona(
                        id, p.getNombre(), p.getApellidos(), p.getEmail(), p.getRol().name(),
                        p.getEmpresa() != null ? p.getEmpresa().getNombre() : null,
                        p.getDepartamento() != null ? p.getDepartamento().getNombre() : null,
                        p.getPuesto(), p.getFechaNacimiento(), p.getHorasSemanales(),
                        p.isActivo(), p.getFechaBaja()),

                timeEntryRepository.findByUsuarioOrderByHoraEntradaAsc(p).stream()
                        .map(f -> new PersonalDataExport.Fichaje(
                                f.getId(), f.getHoraEntrada(), f.getHoraSalida(), f.getSegundosPausaAcumulados(),
                                f.isAnulado(), f.isJornadaIncompleta(),
                                f.getRegistroOriginal() != null ? f.getRegistroOriginal().getId() : null))
                        .toList(),

                addedPauseRepository.findByRegistro_UsuarioOrderByInicioAsc(p).stream()
                        .map(pa -> new PersonalDataExport.PausaAnadida(
                                pa.getRegistro().getId(), pa.getInicio(), pa.getFin(), pa.getMotivo(),
                                pa.getCreadaEn(), pa.getSolicitud() != null, pa.isAnulada()))
                        .toList(),

                absenceRepository.findByUsuario(p).stream()
                        .sorted(Comparator.comparing(a -> a.getFechaInicio()))
                        .map(a -> new PersonalDataExport.Ausencia(
                                a.getFechaInicio(), a.getFechaFin(), a.getTipo().name(), a.getEstado().name(),
                                a.getMotivo(), a.getFechaResolucion(), a.getComentarioResolucion()))
                        .toList(),

                vacationRepository.findByUsuarioOrderByAnioAsc(p).stream()
                        .map(v -> new PersonalDataExport.SaldoVacaciones(v.getAnio(), v.getDiasTotales()))
                        .toList(),

                correctionRepository.findMias(id).stream()
                        .map(c -> new PersonalDataExport.Correccion(
                                c.getRegistro().getId(), c.getHoraEntradaPropuesta(), c.getHoraSalidaPropuesta(),
                                c.getPausaInicioPropuesta(), c.getPausaFinPropuesta(), c.getMotivo(),
                                c.getEstado().name(), c.getCreadoEn(), c.getFechaResolucion(),
                                c.getComentarioResolucion()))
                        .toList(),

                // Todo el rango: no hay "horas extra de otro año" que dejar fuera.
                overtimeRepository.findDeUsuarioEnRango(id, LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31))
                        .stream()
                        .map(h -> new PersonalDataExport.HorasExtra(
                                h.getFecha(), h.getTipo().name(), h.getMinutosExtra(), h.getMinutosEsperados(),
                                h.getEstado().name(), h.getJustificacion(), h.getFechaRevision()))
                        .toList(),

                assignmentRepository.findDeUsuario(id).stream()
                        .map(a -> new PersonalDataExport.Proyecto(
                                a.getProyecto().getCodigo(), a.getProyecto().getNombre(),
                                a.getFechaInicio(), a.getFechaFin()))
                        .toList(),

                // El horario teórico también es un dato de la persona: dice a
                // qué hora tenía que estar en el trabajo cada día.
                scheduleAssignmentRepository.findDeUsuario(id).stream()
                        .map(c -> new PersonalDataExport.Cuadrante(
                                c.getPlantilla().getNombre(), c.getFechaInicio(), c.getFechaFin()))
                        .toList(),

                scheduleExceptionRepository.findByUsuario_IdOrderByFechaDesc(id).stream()
                        .map(e -> new PersonalDataExport.ExcepcionDeCuadrante(
                                e.getFecha(), e.getTipo().name(),
                                e.getInicio() != null ? ReglasDeCuadrante.hora(e.getInicio()) : null,
                                e.getFin() != null ? ReglasDeCuadrante.hora(e.getFin()) : null,
                                e.getMotivo()))
                        .toList(),

                scheduleIncidentRepository.findByUsuario_IdOrderByFechaDesc(id).stream()
                        .map(i -> new PersonalDataExport.IncidenciaDeCuadrante(
                                i.getFecha(), i.getTipo().name(), i.getMinutos(),
                                ReglasDeCuadrante.hora(i.getHoraPrevista()), i.getEstado().name(),
                                i.getJustificacion(), i.getComentarioResolucion()))
                        .toList(),

                noticeRepository.findByDestinatarioOrderByCreadoEnDesc(p).stream()
                        .map(n -> new PersonalDataExport.Aviso(
                                n.getTipo().name(), n.getTitulo(), n.getCuerpo(), n.isLeido(), n.getCreadoEn()))
                        .toList(),

                attachmentRepository.findByUsuarioOrderBySubidoEnDesc(p).stream()
                        .map(ad -> new PersonalDataExport.Adjunto(
                                ad.getTipo().name(), ad.getNombreOriginal(), ad.getMime(), ad.getTamanoBytes(),
                                ad.getSubidoEn(), ad.isVigente()))
                        .toList(),

                applicationRepository.findMias(id).stream()
                        .map(ca -> new PersonalDataExport.Candidatura(
                                ca.getOferta().getTitulo(), ca.getCarta(), ca.getEstado().name(),
                                ca.getCreadoEn(), ca.getFechaResolucion()))
                        .toList(),

                complaintRepository.findMias(id).stream()
                        .map(d -> new PersonalDataExport.Denuncia(
                                d.getCategoria().name(), d.getDescripcion(), d.getEstado().name(),
                                d.getCreadoEn(), d.getAcuseReciboEn(), d.getResueltaEn()))
                        .toList(),

                NOTAS);

        // Sin el contenido: solo que se ejerció el derecho. Es lo que habría
        // que poder acreditar si alguien preguntara si se atendió.
        log.info("Exportación de datos personales del usuario {}", id);
        return exportacion;
    }
}
