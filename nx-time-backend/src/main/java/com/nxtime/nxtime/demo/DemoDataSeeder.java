package com.nxtime.nxtime.demo;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Attachment;
import com.nxtime.nxtime.domain.AttachmentData;
import com.nxtime.nxtime.domain.AttachmentType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.CorrectionRequest;
import com.nxtime.nxtime.domain.CorrectionStatus;
import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.HolidayScope;
import com.nxtime.nxtime.domain.Notice;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.Project;
import com.nxtime.nxtime.domain.ProjectAssignment;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.VacationBalance;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.AttachmentDataRepository;
import com.nxtime.nxtime.repository.AttachmentRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.CorrectionRequestRepository;
import com.nxtime.nxtime.repository.DepartmentRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.repository.NoticeRepository;
import com.nxtime.nxtime.repository.ProjectAssignmentRepository;
import com.nxtime.nxtime.repository.ProjectRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.repository.VacationBalanceRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.nxtime.nxtime.service.NationalHolidayGenerator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rellena la base de datos con datos de ejemplo realistas, para que
 * quien abra el proyecto (o una entrevista técnica) vea algo con
 * contenido en 30 segundos, sin tener que fichar y pedir ausencias a
 * mano primero.
 *
 * Solo se activa con el perfil "demo" (--spring.profiles.active=dev,demo
 * o el que corresponda), y es idempotente: si ya hay al menos una
 * empresa, no vuelve a insertar nada -- así se puede reiniciar la
 * aplicación en modo demo sin duplicar datos ni chocar con las
 * restricciones UNIQUE del esquema.
 *
 * Va directo a los repositorios, no a los servicios: los servicios
 * fichan "ahora" (Instant.now()), y aquí necesitamos fechas pasadas
 * repartidas en los últimos ~3 meses. No sustituye a los tests: no
 * comprueba nada, solo escribe datos de ejemplo.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId MADRID_ZONE = ZoneId.of("Europe/Madrid");
    private static final String DEMO_PASSWORD = "demo1234";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final AbsenceRequestRepository absenceRequestRepository;
    private final HolidayRepository holidayRepository;
    private final DepartmentRepository departmentRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentDataRepository attachmentDataRepository;
    private final NoticeRepository noticeRepository;
    private final VacationBalanceRepository vacationBalanceRepository;
    private final CorrectionRequestRepository correctionRequestRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            CompanyRepository companyRepository,
            UserRepository userRepository,
            TimeEntryRepository timeEntryRepository,
            AbsenceRequestRepository absenceRequestRepository,
            HolidayRepository holidayRepository,
            DepartmentRepository departmentRepository,
            AttachmentRepository attachmentRepository,
            AttachmentDataRepository attachmentDataRepository,
            NoticeRepository noticeRepository,
            VacationBalanceRepository vacationBalanceRepository,
            CorrectionRequestRepository correctionRequestRepository,
            ProjectRepository projectRepository,
            ProjectAssignmentRepository projectAssignmentRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.absenceRequestRepository = absenceRequestRepository;
        this.holidayRepository = holidayRepository;
        this.departmentRepository = departmentRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentDataRepository = attachmentDataRepository;
        this.noticeRepository = noticeRepository;
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.correctionRequestRepository = correctionRequestRepository;
        this.projectRepository = projectRepository;
        this.projectAssignmentRepository = projectAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (companyRepository.count() > 0) {
            log.info("Datos de demo ya presentes ({} empresas) -- no se vuelve a sembrar.", companyRepository.count());
            return;
        }

        log.info("Sembrando datos de demo...");

        Company techCorp = crearEmpresa("TechCorp Solutions");
        User gestorTech = crearUsuario("Marta", "Sánchez Prieto", "marta.sanchez@techcorp.demo", Role.GESTOR, techCorp);
        // Estos dos roles nacieron en la Fase 4, después de escribirse el
        // seeder, y sin ellos la demo no llegaba a lo que mejor la
        // distingue: los informes mensuales, la corrección de fichajes y
        // la línea temporal de auditoría son de RRHH/ADMIN, no de GESTOR.
        User rrhhTech = crearUsuario("Elena", "Ríos Bravo", "elena.rios@techcorp.demo", Role.RRHH, techCorp);
        crearUsuario("Raúl", "Ortega Lima", "raul.ortega@techcorp.demo", Role.ADMIN, techCorp);
        List<User> empleadosTech = List.of(
                crearUsuario("Javier", "López Serna", "javier.lopez@techcorp.demo", Role.EMPLEADO, techCorp),
                crearUsuario("Ana", "Fernández Gil", "ana.fernandez@techcorp.demo", Role.EMPLEADO, techCorp),
                crearUsuario("Carlos", "Ruiz Alonso", "carlos.ruiz@techcorp.demo", Role.EMPLEADO, techCorp),
                crearUsuario("Lucía", "Moreno Vega", "lucia.moreno@techcorp.demo", Role.EMPLEADO, techCorp)
        );

        Company consultoraIberica = crearEmpresa("Consultora Ibérica");
        User gestorIberica = crearUsuario("Pedro", "Navarro Cid", "pedro.navarro@iberica.demo", Role.GESTOR, consultoraIberica);
        List<User> empleadosIberica = List.of(
                crearUsuario("Sofía", "Domínguez Paz", "sofia.dominguez@iberica.demo", Role.EMPLEADO, consultoraIberica),
                crearUsuario("Diego", "Vázquez Cruz", "diego.vazquez@iberica.demo", Role.EMPLEADO, consultoraIberica),
                crearUsuario("Elena", "Castro Nieto", "elena.castro@iberica.demo", Role.EMPLEADO, consultoraIberica)
        );

        for (User empleado : empleadosTech) {
            sembrarFichajes(empleado, techCorp);
        }
        for (User empleado : empleadosIberica) {
            sembrarFichajes(empleado, consultoraIberica);
        }

        sembrarFestivos(techCorp);
        sembrarFestivos(consultoraIberica);

        // Fase D. Sin esto, las barras de horas por proyecto salen
        // vacías en la demo desplegada y la pantalla de proyectos
        // parece no hacer nada.
        sembrarProyectos(empleadosTech, techCorp, "NX-CORE", "NX-APP");
        sembrarProyectos(empleadosIberica, consultoraIberica, "CI-AUDIT", "CI-ERP");

        // El gestor de cada empresa es quien resuelve las peticiones de
        // sus empleados: desde la Fase 9 una petición resuelta SIEMPRE
        // tiene resolutor y fecha (lo comprueba la propia base de datos,
        // ck_peticiones_resolucion_coherente).
        sembrarAusencias(empleadosTech, gestorTech);
        sembrarAusencias(empleadosIberica, gestorIberica);

        // Fase A. Sin esto la campana sale a cero y el diálogo de ficha
        // enseña 40 h y 22 días para toda la plantilla: las dos
        // pantallas nuevas parecerían no hacer nada en la demo
        // desplegada, que es justo lo que ve quien abre el proyecto.
        // Fase B. Sin departamentos ni datos personales, el perfil sale
        // con la mitad de los campos vacíos en la demo desplegada y el
        // avatar de todo el mundo es una letra suelta.
        sembrarPerfiles(empleadosTech, techCorp, "Ingeniería", "Producto");
        sembrarPerfiles(empleadosIberica, consultoraIberica, "Consultoría", "Administración");

        sembrarFichas(empleadosTech);
        sembrarFichas(empleadosIberica);

        // Fase B2. Solo a algunos: la pantalla tiene que enseñar tanto un
        // avatar con foto como uno con iniciales, y un perfil con CV como
        // uno sin él.
        sembrarAdjuntos(empleadosTech);
        sembrarAdjuntos(empleadosIberica);
        // Fase E. Sin esto, la pantalla de correcciones sale vacia en
        // la demo desplegada y no se ve lo unico que la distingue: que
        // una correccion la tiene que aceptar alguien.
        sembrarCorrecciones(empleadosTech, rrhhTech);

        sembrarAvisos(empleadosTech, gestorTech);
        sembrarAvisos(empleadosIberica, gestorIberica);

        log.info(
                "Datos de demo listos: 2 empresas, {} usuarios (contraseña '{}'). "
                        + "Gestores: {} / {}. RRHH (informes, correcciones y auditoría): {}.",
                userRepository.count(), DEMO_PASSWORD,
                gestorTech.getEmail(), gestorIberica.getEmail(), rrhhTech.getEmail()
        );
    }

    private Company crearEmpresa(String nombre) {
        return companyRepository.save(Company.builder().nombre(nombre).build());
    }

    /**
     * El nombre y los apellidos van SEPARADOS desde la Fase B.
     *
     * Antes el seeder metía "Javier López" entero en `nombre`, que era
     * inofensivo mientras no existía la columna `apellidos` -- pero en
     * cuanto existe, añadirle unos apellidos deja a la gente llamándose
     * "Javier López García Ruiz". Y las iniciales del avatar salían de
     * las dos primeras letras del nombre ("JA") en vez de ser una por
     * palabra ("JL").
     */
    private User crearUsuario(String nombre, String apellidos, String email, Role rol, Company empresa) {
        return userRepository.save(User.builder()
                .nombre(nombre)
                .apellidos(apellidos)
                .email(email)
                .contrasena(passwordEncoder.encode(DEMO_PASSWORD))
                .rol(rol)
                .empresa(empresa)
                .build());
    }

    /**
     * Genera fichajes de los últimos ~90 días naturales, solo días
     * laborables (lunes a viernes), con hora de entrada/salida y pausa
     * de comida con un poco de variación para que no se vean todos
     * idénticos. El fichaje de hoy se deja ABIERTO (sin horaSalida) para
     * la mitad de los empleados, así la demo también puede mostrar el
     * endpoint "fichaje activo" sin que haya que fichar a mano primero.
     */
    private void sembrarFichajes(User empleado, Company empresa) {
        LocalDate hoy = LocalDate.now(MADRID_ZONE);
        LocalDate inicio = hoy.minusDays(90);
        // Variación determinista por usuario, para que no todos los
        // empleados entren/salgan exactamente a la misma hora.
        int jitterMinutos = (int) (empleado.getId() % 20) - 10;
        boolean dejarJornadaDeHoyAbierta = empleado.getId() % 2 == 0;

        for (LocalDate fecha = inicio; !fecha.isAfter(hoy); fecha = fecha.plusDays(1)) {
            if (fecha.getDayOfWeek().getValue() >= 6) {
                continue; // fin de semana
            }

            boolean esHoy = fecha.isEqual(hoy);
            if (esHoy && !dejarJornadaDeHoyAbierta) {
                continue; // este empleado aún no ha fichado hoy en la demo
            }

            Instant horaEntrada = ZonedDateTime.of(fecha, LocalTime.of(9, 0).plusMinutes(jitterMinutos), MADRID_ZONE)
                    .toInstant();

            TimeEntry.TimeEntryBuilder builder = TimeEntry.builder()
                    .usuario(empleado)
                    .empresa(empresa)
                    .horaEntrada(horaEntrada)
                    .enPausa(false);

            if (esHoy) {
                // Jornada de hoy: abierta, sin pausas todavía.
                builder.segundosPausaAcumulados(0);
            } else {
                Instant horaSalida = ZonedDateTime.of(fecha, LocalTime.of(17, 30).plusMinutes(jitterMinutos), MADRID_ZONE)
                        .toInstant();
                long segundosPausa = ChronoUnit.SECONDS.between(
                        LocalTime.of(0, 0), LocalTime.of(0, 30).plusMinutes(jitterMinutos % 5)); // ~30 min de comida
                builder.horaSalida(horaSalida).segundosPausaAcumulados(Math.max(segundosPausa, 900));
            }

            timeEntryRepository.save(builder.build());
        }
    }

    /**
     * Festivos de ejemplo: los diez nacionales del año y tres propios de
     * la empresa, uno de cada ámbito.
     *
     * Los nacionales ya no se teclean aquí (Fase C): los da
     * {@link NationalHolidayGenerator}, el mismo que usa la aplicación
     * cuando alguien mira un año por primera vez. Mantener dos listas
     * habría acabado con la demo enseñando festivos que la aplicación no
     * calcula, o al revés -- y siete de los diez es justo lo que había.
     *
     * Los tres de empresa son distintos a propósito: sin un autonómico y
     * un local, el calendario de la demo no enseñaría más que un color y
     * la distinción de ámbitos parecería decorativa.
     */
    private void sembrarFestivos(Company empresa) {
        int anio = LocalDate.now(MADRID_ZONE).getYear();

        // Los nacionales se guardan SIN empresa (empresa == null), así
        // aplican a todas -- ver Holiday. Solo los siembra la primera
        // empresa; para la segunda ya existen (uq_festivos_nacional_fecha).
        if (holidayRepository.count() == 0) {
            holidayRepository.saveAll(NationalHolidayGenerator.delAnio(anio));
        }

        crearFestivoDeEmpresa(empresa, LocalDate.of(anio, 5, 2),
                "Fiesta de la Comunidad de Madrid", HolidayScope.AUTONOMICO);
        crearFestivoDeEmpresa(empresa, LocalDate.of(anio, 5, 15),
                "San Isidro", HolidayScope.LOCAL);
        crearFestivoDeEmpresa(empresa, LocalDate.of(anio, 7, 25),
                "Día de convenio de " + empresa.getNombre(), HolidayScope.EMPRESA);
    }

    /**
     * Dos proyectos por empresa y un <b>relevo real</b> a mitad del
     * periodo sembrado (Fase D).
     *
     * El relevo es lo importante: la mitad de la plantilla empieza en el
     * primer proyecto y se cambia al segundo hace 45 días, cerrando la
     * asignación anterior el día antes. Sin eso, la demo enseñaría a todo
     * el mundo con una única asignación abierta y la vigencia —que es la
     * razón de ser de esta fase— no se vería por ninguna parte: las horas
     * de esas personas aparecen repartidas entre los DOS proyectos, cada
     * tramo en el suyo.
     *
     * Los fichajes sembrados cubren los últimos 90 días
     * ({@link #sembrarFichajes}), así que el corte a 45 parte el periodo
     * por la mitad y los dos proyectos salen con horas.
     */
    private void sembrarProyectos(List<User> empleados, Company empresa, String codigoUno, String codigoDos) {
        LocalDate hoy = LocalDate.now(MADRID_ZONE);
        LocalDate inicio = hoy.minusDays(90);
        LocalDate relevo = hoy.minusDays(45);

        Project primero = projectRepository.save(Project.builder()
                .empresa(empresa)
                .codigo(codigoUno)
                .nombre("Plataforma " + empresa.getNombre())
                .descripcion("Mantenimiento y evolución de la plataforma principal.")
                .fechaInicio(inicio)
                .activo(true)
                .build());

        Project segundo = projectRepository.save(Project.builder()
                .empresa(empresa)
                .codigo(codigoDos)
                .nombre("Nueva aplicación móvil")
                .descripcion("Desarrollo del cliente móvil.")
                .fechaInicio(relevo)
                .activo(true)
                .build());

        for (int i = 0; i < empleados.size(); i++) {
            User empleado = empleados.get(i);

            if (i % 2 == 0) {
                // Se queda en el primero todo el periodo.
                crearAsignacion(empresa, empleado, primero, inicio, null);
            } else {
                // Relevo: cierra en el primero el día ANTES de empezar en
                // el segundo. Si las dos asignaciones compartieran el día
                // del relevo, el EXCLUDE de la base rechazaría la
                // segunda -- que es exactamente lo que tiene que hacer.
                crearAsignacion(empresa, empleado, primero, inicio, relevo.minusDays(1));
                crearAsignacion(empresa, empleado, segundo, relevo, null);
            }
        }
    }

    private void crearAsignacion(
            Company empresa, User empleado, Project proyecto, LocalDate desde, LocalDate hasta) {
        projectAssignmentRepository.save(ProjectAssignment.builder()
                .empresa(empresa)
                .usuario(empleado)
                .proyecto(proyecto)
                .fechaInicio(desde)
                .fechaFin(hasta)
                .build());
    }

    private void crearFestivoDeEmpresa(
            Company empresa, LocalDate fecha, String descripcion, HolidayScope ambito) {
        holidayRepository.save(Holiday.builder()
                .empresa(empresa)
                .fecha(fecha)
                .descripcion(descripcion)
                .ambito(ambito)
                .build());
    }

    /**
     * Dos peticiones de ausencia por empleado: una ya resuelta (mitad
     * aprobadas, mitad rechazadas) y una pendiente, para que los tres
     * estados tengan ejemplos en la demo.
     */
    private void sembrarAusencias(List<User> empleados, User gestor) {
        LocalDate hoy = LocalDate.now(MADRID_ZONE);

        for (int i = 0; i < empleados.size(); i++) {
            User empleado = empleados.get(i);

            AbsenceStatus estadoResuelto = (i % 2 == 0) ? AbsenceStatus.APROBADA : AbsenceStatus.RECHAZADA;
            absenceRequestRepository.save(AbsenceRequest.builder()
                    .usuario(empleado)
                    .empresa(empleado.getEmpresa())
                    .fechaInicio(hoy.minusDays(30 + i))
                    .fechaFin(hoy.minusDays(28 + i))
                    .tipo(AbsenceType.VACACIONES)
                    .motivo("Vacaciones de ejemplo")
                    .estado(estadoResuelto)
                    .aprobadoPor(gestor)
                    .fechaResolucion(Instant.now())
                    .comentarioResolucion(estadoResuelto == AbsenceStatus.APROBADA
                            ? "Aprobada, que las disfrutes."
                            : "Rechazada: esas fechas coinciden con el cierre trimestral.")
                    .build());

            absenceRequestRepository.save(AbsenceRequest.builder()
                    .usuario(empleado)
                    .empresa(empleado.getEmpresa())
                    .fechaInicio(hoy.plusDays(15 + i))
                    .fechaFin(hoy.plusDays(16 + i))
                    .tipo(AbsenceType.ASUNTOS_PROPIOS)
                    .motivo("Petición de ejemplo pendiente de revisar")
                    .estado(AbsenceStatus.PENDIENTE)
                    .build());
        }
    }

    /**
     * Fichas variadas (Fase A): una jornada reducida y un derecho de
     * vacaciones por encima del mínimo.
     *
     * Hasta la Fase A nadie escribía nunca en "saldo_vacaciones" ni en
     * "usuarios.horas_semanales", así que en la demo todo el mundo salía
     * con 40 h y 22 días y el formulario de ficha parecía decorativo.
     * Con esto se ve de un vistazo que los dos campos son de verdad
     * editables y que el saldo por defecto convive con el explícito.
     */
    private void sembrarFichas(List<User> empleados) {
        if (empleados.isEmpty()) {
            return;
        }
        int anio = LocalDate.now(MADRID_ZONE).getYear();

        // Al primero, jornada reducida de 37,5 h: el caso que justifica
        // que la columna sea NUMERIC(4,1) y no un entero.
        User jornadaReducida = empleados.get(0);
        jornadaReducida.setHorasSemanales(new BigDecimal("37.5"));
        userRepository.save(jornadaReducida);

        // Al último, 25 días por convenio. Los demás se quedan sin fila
        // y heredan los 22 de DIAS_POR_DEFECTO, que es el caso normal.
        User conConvenio = empleados.get(empleados.size() - 1);
        vacationBalanceRepository.save(VacationBalance.builder()
                .usuario(conConvenio)
                .anio(anio)
                .diasTotales(25)
                .build());
    }

    /**
     * Departamentos y datos personales (Fase B).
     *
     * El puesto y la fecha de nacimiento solo a algunos, a propósito:
     * son opcionales de verdad y la pantalla tiene que aguantar verlos
     * vacíos.
     */
    private void sembrarPerfiles(List<User> empleados, Company empresa, String... nombresDeDepartamento) {
        List<Department> departamentos = java.util.Arrays.stream(nombresDeDepartamento)
                .map(nombre -> departmentRepository.save(
                        Department.builder().empresa(empresa).nombre(nombre).build()))
                .toList();

        // Los apellidos los pone ya crearUsuario: son lo que hace que el
        // avatar enseñe dos iniciales de verdad ("JL" y no "JA").
        // Puestos sin marca de género: se reparten por posición entre
        // nombres de hombre y de mujer, y "Desarrolladora backend" en la
        // ficha de Javier canta.
        String[] puestos = {"Desarrollo backend", "Soporte técnico", null, "Análisis de datos"};

        for (int i = 0; i < empleados.size(); i++) {
            User empleado = empleados.get(i);
            empleado.setPuesto(puestos[i % puestos.length]);
            // Uno de cada tres sin fecha de nacimiento: es opcional y la
            // pantalla tiene que enseñarlo así.
            if (i % 3 != 2) {
                empleado.setFechaNacimiento(LocalDate.of(1988 + i, 1 + (i * 3) % 12, 5 + i));
            }
            empleado.setDepartamento(departamentos.get(i % departamentos.size()));
            userRepository.save(empleado);
        }
    }

    /**
     * Un CV y una foto de ejemplo (Fase B2), a la mitad de la plantilla.
     *
     * El PDF y el JPEG se generan aquí en vez de leerlos de
     * `src/main/resources`: son ficheros mínimos válidos, y meter
     * binarios en el repositorio para que la demo tenga un adjunto sería
     * pagar un precio permanente por un dato de ejemplo.
     *
     * La foto se guarda ya reescalada, igual que haría el servicio: el
     * seeder va a los repositorios y no a los servicios (ver el Javadoc
     * de la clase), así que le toca respetar la misma invariante.
     */
    private void sembrarAdjuntos(List<User> empleados) {
        for (int i = 0; i < empleados.size(); i++) {
            // Uno sí y uno no: el avatar con iniciales tiene que verse
            // igual de bien que el que tiene foto.
            if (i % 2 != 0) {
                continue;
            }
            User empleado = empleados.get(i);
            guardarAdjunto(empleado, AttachmentType.CV, "cv-" + empleado.getId() + ".pdf",
                    "application/pdf", pdfMinimo(empleado));
            guardarAdjunto(empleado, AttachmentType.FOTO, "foto.jpg",
                    "image/jpeg", avatarDeEjemplo(empleado));
        }
    }

    private void guardarAdjunto(User empleado, AttachmentType tipo, String nombre,
                                String mime, byte[] contenido) {
        if (contenido == null) {
            return;
        }
        Attachment adjunto = attachmentRepository.save(Attachment.builder()
                .empresa(empleado.getEmpresa())
                .usuario(empleado)
                .tipo(tipo)
                .nombreOriginal(nombre)
                .mime(mime)
                .tamanoBytes(contenido.length)
                .subidoEn(Instant.now())
                .build());
        attachmentDataRepository.save(AttachmentData.builder()
                .adjuntoId(adjunto.getId())
                .contenido(contenido)
                .build());
    }

    /**
     * Un PDF de una página en blanco: lo mínimo que un visor acepta.
     *
     * Empieza por "%PDF-", que es justo lo que mira la validación por
     * contenido de {@code AttachmentService}, así que el CV de demo pasa
     * por el mismo aro que uno subido de verdad.
     */
    private byte[] pdfMinimo(User empleado) {
        // Se concatena en vez de usar String.formatted(): un PDF está
        // lleno de '%' ("%PDF-1.4", "%%EOF") y el formateador los toma
        // por conversiones, así que revienta con
        // UnknownFormatConversionException al arrancar.
        String pdf = """
                %PDF-1.4
                1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
                2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
                3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]>>endobj
                trailer<</Root 1 0 R>>
                % CV de ejemplo de """
                + empleado.getNombre() + "\n%%EOF";
        return pdf.getBytes(StandardCharsets.UTF_8);
    }

    /** Un JPEG de 256x256 de un color liso, distinto por persona. */
    private byte[] avatarDeEjemplo(User empleado) {
        BufferedImage imagen = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D lienzo = imagen.createGraphics();
        try {
            // Un tono por persona, para que no salgan todos iguales.
            lienzo.setColor(Color.getHSBColor((empleado.getId() % 10) / 10f, 0.45f, 0.75f));
            lienzo.fillRect(0, 0, 256, 256);
        } finally {
            lienzo.dispose();
        }
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "jpg", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            // Los datos de demo no valen una excepción que impida
            // arrancar: sin foto, el avatar enseña las iniciales.
            log.warn("No se ha podido generar el avatar de demo de {}", empleado.getEmail());
            return null;
        }
    }

    /**
     * Avisos de ejemplo (Fase A), en correspondencia con las ausencias
     * que acaba de sembrar {@link #sembrarAusencias}: al empleado, el de
     * su ausencia resuelta; al gestor, uno por cada petición que tiene
     * pendiente de resolver.
     *
     * Se dejan algunos SIN LEER a propósito -- si no, la campana saldría
     * a cero y no se vería el contador, que es lo que la fase añade -- y
     * con fechas escalonadas hacia atrás, porque una lista donde todo
     * tiene la misma marca de tiempo se lee como datos falsos.
     */
    /**
     * Tres solicitudes de correccion, una por cada situacion que el
     * flujo distingue (Fase E).
     *
     * Son tres y no una porque el estado por si solo no cuenta la
     * historia: lo que cambia entre ellas es QUIEN tiene que resolver, y
     * eso solo se ve con una pedida por el empleado, otra pedida sobre
     * el, y una que acabo en disputa. Con un unico ejemplo, la pantalla
     * de correcciones parece una lista de pendientes cualquiera.
     */
    private void sembrarCorrecciones(List<User> empleados, User rrhh) {
        if (empleados.size() < 3) {
            return;
        }

        // 1. La pide el propio empleado: espera a que la apruebe alguien
        //    con "correccion:aprobar".
        crearSolicitud(empleados.get(0), empleados.get(0),
                "Olvide fichar la salida y me la cerro el proceso nocturno.",
                CorrectionStatus.PENDIENTE, null);

        // 2. La pide RRHH sobre el fichaje de otra persona: quien decide
        //    es ELLA, aunque no tenga ninguna authority de aprobacion.
        crearSolicitud(empleados.get(1), rrhh,
                "El reloj de la entrada iba adelantado ese dia.",
                CorrectionStatus.PENDIENTE, null);

        // 3. Igual que la anterior, pero el empleado no la acepta: pasa a
        //    disputa y la resuelve RRHH en firme.
        crearSolicitud(empleados.get(2), rrhh,
                "Ajuste de la hora de salida segun el parte del centro.",
                CorrectionStatus.EN_DISPUTA,
                "Ese dia sali a la hora que fiche; tengo el correo de salida.");
    }

    private void crearSolicitud(
            User dueno, User solicitante, String motivo, CorrectionStatus estado, String motivoDisputa) {
        // Sobre una jornada ya cerrada: una activa no se puede corregir.
        TimeEntry fichaje = timeEntryRepository.findHistoryByUsuario(dueno, PageRequest.of(0, 5)).stream()
                .filter(registro -> registro.getHoraSalida() != null && !registro.isAnulado())
                .findFirst()
                .orElse(null);
        if (fichaje == null) {
            return;
        }

        correctionRequestRepository.save(CorrectionRequest.builder()
                .empresa(dueno.getEmpresa())
                .registro(fichaje)
                .solicitante(solicitante)
                .horaEntradaPropuesta(fichaje.getHoraEntrada().minus(15, ChronoUnit.MINUTES))
                .horaSalidaPropuesta(fichaje.getHoraSalida())
                .motivo(motivo)
                .estado(estado)
                .motivoDisputa(motivoDisputa)
                .creadoEn(Instant.now().minus(1, ChronoUnit.DAYS))
                .build());
    }

    private void sembrarAvisos(List<User> empleados, User gestor) {
        Instant ahora = Instant.now();

        for (int i = 0; i < empleados.size(); i++) {
            User empleado = empleados.get(i);
            boolean aprobada = i % 2 == 0;

            crearAviso(empleado, NoticeType.BIENVENIDA,
                    "Bienvenido a " + empleado.getEmpresa().getNombre(),
                    "Tu cuenta ya está activa. Desde aquí puedes fichar tu jornada y pedir ausencias.",
                    ahora.minus(60, ChronoUnit.DAYS), true);

            crearAviso(empleado, NoticeType.AUSENCIA_RESUELTA,
                    "Tu ausencia ha sido " + (aprobada ? "aprobada" : "rechazada"),
                    "VACACIONES. " + (aprobada
                            ? "Aprobada, que las disfrutes."
                            : "Rechazada: esas fechas coinciden con el cierre trimestral."),
                    ahora.minus(30L + i, ChronoUnit.DAYS), false);

            // El gestor ve una petición pendiente por cada empleado: es
            // lo que hace que su campana lleve un número de verdad.
            crearAviso(gestor, NoticeType.AUSENCIA_SOLICITADA,
                    "Nueva petición de " + empleado.getNombre(),
                    "ASUNTOS_PROPIOS, pendiente de resolver.",
                    ahora.minus(2L + i, ChronoUnit.HOURS), false);
        }
    }

    private void crearAviso(User destinatario, NoticeType tipo, String titulo,
                            String cuerpo, Instant creadoEn, boolean leido) {
        noticeRepository.save(Notice.builder()
                .empresa(destinatario.getEmpresa())
                .destinatario(destinatario)
                .tipo(tipo)
                .titulo(titulo)
                .cuerpo(cuerpo)
                .rutaDestino(tipo.getRutaDestinoPorDefecto())
                .leido(leido)
                .creadoEn(creadoEn)
                .build());
    }
}
