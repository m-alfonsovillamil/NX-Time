package com.nxtime.nxtime.demo;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Holiday;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.HolidayRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
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
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            CompanyRepository companyRepository,
            UserRepository userRepository,
            TimeEntryRepository timeEntryRepository,
            AbsenceRequestRepository absenceRequestRepository,
            HolidayRepository holidayRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.absenceRequestRepository = absenceRequestRepository;
        this.holidayRepository = holidayRepository;
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
        User gestorTech = crearUsuario("Marta Sánchez", "marta.sanchez@techcorp.demo", Role.GESTOR, techCorp);
        List<User> empleadosTech = List.of(
                crearUsuario("Javier López", "javier.lopez@techcorp.demo", Role.EMPLEADO, techCorp),
                crearUsuario("Ana Fernández", "ana.fernandez@techcorp.demo", Role.EMPLEADO, techCorp),
                crearUsuario("Carlos Ruiz", "carlos.ruiz@techcorp.demo", Role.EMPLEADO, techCorp),
                crearUsuario("Lucía Moreno", "lucia.moreno@techcorp.demo", Role.EMPLEADO, techCorp)
        );

        Company consultoraIberica = crearEmpresa("Consultora Ibérica");
        User gestorIberica = crearUsuario("Pedro Navarro", "pedro.navarro@iberica.demo", Role.GESTOR, consultoraIberica);
        List<User> empleadosIberica = List.of(
                crearUsuario("Sofía Domínguez", "sofia.dominguez@iberica.demo", Role.EMPLEADO, consultoraIberica),
                crearUsuario("Diego Vázquez", "diego.vazquez@iberica.demo", Role.EMPLEADO, consultoraIberica),
                crearUsuario("Elena Castro", "elena.castro@iberica.demo", Role.EMPLEADO, consultoraIberica)
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

        log.info(
                "Datos de demo listos: 2 empresas, {} usuarios, credenciales de gestor {} / {} (contraseña '{}').",
                2 + empleadosTech.size() + 1 + empleadosIberica.size() + 1,
                gestorTech.getEmail(), gestorIberica.getEmail(), DEMO_PASSWORD
        );
    }

    private Company crearEmpresa(String nombre) {
        return companyRepository.save(Company.builder().nombre(nombre).build());
    }

    private User crearUsuario(String nombre, String email, Role rol, Company empresa) {
        return userRepository.save(User.builder()
                .nombre(nombre)
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
}
