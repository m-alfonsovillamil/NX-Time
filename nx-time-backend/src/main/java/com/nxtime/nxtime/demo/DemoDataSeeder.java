package com.nxtime.nxtime.demo;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Department;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.Notice;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.domain.VacationBalance;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.DepartmentRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.repository.NoticeRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.repository.VacationBalanceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
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
    private final NoticeRepository noticeRepository;
    private final VacationBalanceRepository vacationBalanceRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            CompanyRepository companyRepository,
            UserRepository userRepository,
            TimeEntryRepository timeEntryRepository,
            AbsenceRequestRepository absenceRequestRepository,
            HolidayRepository holidayRepository,
            DepartmentRepository departmentRepository,
            NoticeRepository noticeRepository,
            VacationBalanceRepository vacationBalanceRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.absenceRequestRepository = absenceRequestRepository;
        this.holidayRepository = holidayRepository;
        this.departmentRepository = departmentRepository;
        this.noticeRepository = noticeRepository;
        this.vacationBalanceRepository = vacationBalanceRepository;
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
     * Festivos de ejemplo (Fase 9): unos cuantos nacionales fijos, que
     * caen igual todos los años, y uno propio de la empresa. Sirven para
     * que el cálculo de días hábiles de las vacaciones (ver
     * WorkingDayService) tenga algo real que descontar en la demo,
     * además de los fines de semana.
     */
    private void sembrarFestivos(Company empresa) {
        int anio = LocalDate.now(MADRID_ZONE).getYear();

        // Nacionales: se guardan SIN empresa (empresa == null), así
        // aplican a todas -- ver Holiday. Solo los siembra la primera
        // empresa; para la segunda ya existen (uq_festivos_nacional_fecha).
        if (holidayRepository.count() == 0) {
            crearFestivoNacional(LocalDate.of(anio, 1, 1), "Año Nuevo");
            crearFestivoNacional(LocalDate.of(anio, 5, 1), "Día del Trabajador");
            crearFestivoNacional(LocalDate.of(anio, 8, 15), "Asunción de la Virgen");
            crearFestivoNacional(LocalDate.of(anio, 10, 12), "Fiesta Nacional de España");
            crearFestivoNacional(LocalDate.of(anio, 11, 1), "Todos los Santos");
            crearFestivoNacional(LocalDate.of(anio, 12, 6), "Día de la Constitución");
            crearFestivoNacional(LocalDate.of(anio, 12, 25), "Navidad");
        }

        // Propio de esta empresa (día de convenio, puente...).
        holidayRepository.save(Holiday.builder()
                .empresa(empresa)
                .fecha(LocalDate.of(anio, 7, 25))
                .descripcion("Día de convenio de " + empresa.getNombre())
                .build());
    }

    private void crearFestivoNacional(LocalDate fecha, String descripcion) {
        holidayRepository.save(Holiday.builder().fecha(fecha).descripcion(descripcion).build());
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
