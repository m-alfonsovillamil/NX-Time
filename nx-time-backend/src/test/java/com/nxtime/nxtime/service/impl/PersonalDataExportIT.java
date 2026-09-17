package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceStatus;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.AddedPause;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Notice;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.PersonalDataExport;
import com.nxtime.nxtime.report.PersonalDataPdfGenerator;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.AddedPauseRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.NoticeRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.PersonalDataExportService;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Exportación de datos personales (RGPD, arts. 15 y 20) contra PostgreSQL real.
 *
 * Lo que más importa aquí no es que salga todo, sino que <b>no salga nada de
 * otra persona</b>: una exportación que se colara datos de un compañero sería
 * una brecha de datos personales, justo al ejercer un derecho sobre ellos.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class PersonalDataExportIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "export_it_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection("jdbc:postgresql://localhost:5433/nxtime", "nxtime", "nxtime");
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + testDb);
        }
        String testUrl = "jdbc:postgresql://localhost:5433/" + testDb;
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> "nxtime_app");
        registry.add("spring.datasource.password", () -> "nxtime_app");
        registry.add("spring.flyway.url", () -> testUrl);
        registry.add("spring.flyway.user", () -> "nxtime");
        registry.add("spring.flyway.password", () -> "nxtime");
    }

    @Autowired
    private PersonalDataExportService exportService;
    @Autowired
    private PersonalDataPdfGenerator pdfGenerator;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private AddedPauseRepository addedPauseRepository;
    @Autowired
    private AbsenceRequestRepository absenceRepository;
    @Autowired
    private NoticeRepository noticeRepository;

    private Company empresa;
    private User ana;
    private User javi;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        ana = persona("ana" + System.nanoTime() + "@test", "Ana");
        javi = persona("javi" + System.nanoTime() + "@test", "Javi");
    }

    private User persona(String email, String nombre) {
        return userRepository.save(User.builder()
                .nombre(nombre).apellidos("Pruebas").email(email).contrasena("$2a$10$hashquenodebesalir")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).horasSemanales(new BigDecimal("40.0"))
                .fechaNacimiento(LocalDate.of(1990, 5, 17))
                .build());
    }

    private TimeEntry fichaje(User quien, int diasAtras) {
        Instant entrada = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(diasAtras, ChronoUnit.DAYS);
        return timeEntryRepository.save(TimeEntry.builder()
                .usuario(quien).empresa(empresa)
                .horaEntrada(entrada).horaSalida(entrada.plus(8, ChronoUnit.HOURS))
                .segundosPausaAcumulados(1800)
                .build());
    }

    private void datosDe(User quien, int fichajes) {
        TimeEntry ultimo = null;
        for (int i = 1; i <= fichajes; i++) {
            ultimo = fichaje(quien, i);
        }
        addedPauseRepository.save(AddedPause.builder()
                .empresa(empresa).registro(ultimo)
                .inicio(ultimo.getHoraEntrada().plus(4, ChronoUnit.HOURS))
                .fin(ultimo.getHoraEntrada().plus(4, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES))
                .motivo("Comida de " + quien.getNombre()).creadaPor(quien)
                .build());
        absenceRepository.save(AbsenceRequest.builder()
                .usuario(quien).empresa(empresa)
                .fechaInicio(LocalDate.now().plusDays(10)).fechaFin(LocalDate.now().plusDays(12))
                .tipo(AbsenceType.VACACIONES).estado(AbsenceStatus.PENDIENTE)
                .motivo("Viaje de " + quien.getNombre())
                .build());
        noticeRepository.save(Notice.builder()
                .empresa(empresa).destinatario(quien).tipo(NoticeType.BIENVENIDA)
                .titulo("Bienvenida, " + quien.getNombre()).cuerpo("Hola").leido(false).creadoEn(Instant.now())
                .build());
    }

    @Test
    @DisplayName("Salen todos los datos propios, sin el límite de 200 fichajes del historial")
    void salenTodosLosDatosPropios() {
        datosDe(ana, 205);

        PersonalDataExport exportacion = exportService.exportar(ana);

        assertThat(exportacion.persona().email()).isEqualTo(ana.getEmail());
        assertThat(exportacion.persona().fechaNacimiento()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(exportacion.fichajes()).hasSize(205);
        assertThat(exportacion.pausasAnadidas()).singleElement()
                .satisfies(p -> assertThat(p.motivo()).isEqualTo("Comida de Ana"));
        assertThat(exportacion.ausencias()).singleElement()
                .satisfies(a -> assertThat(a.motivo()).isEqualTo("Viaje de Ana"));
        assertThat(exportacion.avisos()).singleElement()
                .satisfies(av -> assertThat(av.titulo()).isEqualTo("Bienvenida, Ana"));
        assertThat(exportacion.notas()).isNotEmpty();
    }

    /*
     * La prueba que importa. Javi es de la MISMA empresa y tiene datos del
     * mismo tipo: si alguna consulta filtrara por empresa en vez de por
     * persona, sus datos saldrían en la exportación de Ana.
     */
    @Test
    @DisplayName("No sale nada de un compañero de la misma empresa")
    void noSaleNadaDeOtraPersona() throws Exception {
        datosDe(ana, 3);
        datosDe(javi, 4);

        PersonalDataExport exportacion = exportService.exportar(ana);
        String json = objectMapper.writeValueAsString(exportacion);

        assertThat(exportacion.fichajes()).hasSize(3);
        assertThat(json).doesNotContain(javi.getEmail(), "Comida de Javi", "Viaje de Javi", "Bienvenida, Javi");
    }

    @Test
    @DisplayName("La contraseña no sale, ni siquiera cifrada")
    void laContrasenaNoSale() throws Exception {
        datosDe(ana, 1);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(exportService.exportar(ana)));

        assertThat(json.toString()).doesNotContain("$2a$10$", "hashquenodebesalir");
        assertThat(json.get("persona").has("contrasena")).isFalse();
    }

    /*
     * El PDF sale del mismo objeto que el JSON. Se comprueba que es un PDF de
     * verdad y que lleva lo propio y nada ajeno: un PDF que se generara vacío
     * o a medias pasaría un test que solo mirase que no lanza.
     */
    @Test
    @DisplayName("El PDF se genera, se puede leer, y tampoco lleva datos de otra persona")
    void elPdfEsLegibleYNoLlevaDatosAjenos() throws Exception {
        datosDe(ana, 2);
        datosDe(javi, 2);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        pdfGenerator.generar(exportService.exportar(ana), salida);

        PdfReader lector = new PdfReader(salida.toByteArray());
        StringBuilder texto = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(lector);
        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            texto.append(extractor.getTextFromPage(pagina));
        }
        assertThat(texto.toString()).contains("Tus datos en NX Time", ana.getEmail(), "Comida de Ana");
        assertThat(texto.toString()).doesNotContain(javi.getEmail(), "Comida de Javi");
        // Las horas del PDF van en hora de España: la nota del JSON diciendo
        // que van en UTC sería falsa aquí.
        assertThat(texto.toString()).doesNotContain("Las horas son instantes en UTC");
    }
}
