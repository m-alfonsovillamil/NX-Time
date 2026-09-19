package com.nxtime.nxtime.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuditIntegrityResponse;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El verificador de la cadena de auditoría, contra una base de datos real.
 *
 * Un test con mocks aquí no valdría de nada: lo que se comprueba es
 * precisamente que el hash se pueda recalcular **después de pasar por
 * PostgreSQL**, que es donde se perdían los datos que hacían falta (los
 * nanosegundos de la marca de tiempo y el texto exacto del JSON). Con objetos
 * en memoria cuadraría siempre, y seguiría sin cuadrar en producción.
 *
 * Y para manipular una fila hay que saltarse el trigger append-only, que es
 * justo el escenario contra el que la cadena existe: alguien con acceso a la
 * base por debajo de la aplicación.
 */
@SpringBootTest
class VerificadorDeAuditoriaIT {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5433/nxtime";
    private static String testUrl;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "verificador_it_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection(ADMIN_URL, "nxtime", "nxtime");
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + testDb);
        }
        testUrl = "jdbc:postgresql://localhost:5433/" + testDb;
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> "nxtime_app");
        registry.add("spring.datasource.password", () -> "nxtime_app");
        registry.add("spring.flyway.url", () -> testUrl);
        registry.add("spring.flyway.user", () -> "nxtime");
        registry.add("spring.flyway.password", () -> "nxtime");
    }

    @Autowired
    private TimeEntryService timeEntryService;
    @Autowired
    private TimeEntryAuditRepository auditRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private VerificadorDeAuditoria verificador;

    /**
     * Cada test arranca con la traza vacía.
     *
     * Comparten base de datos, y una cadena rota lo está para siempre: sin
     * esto, el test que manipula una fila dejaba en rojo a todos los que
     * corrieran después, y el fallo aparecía en la fila de otro test. Vaciarla
     * exige saltarse el trigger, igual que manipularla.
     */
    @BeforeEach
    void limpiarLaTraza() throws Exception {
        manipularSaltandoseElTrigger("TRUNCATE auditoria_fichaje");
    }

    /**
     * Una jornada entera: entrada, pausa y salida. Tres filas de auditoría
     * encadenadas, con JSON en los dos valores, que es el caso que antes no
     * se podía recalcular.
     */
    private void jornadaCompleta(String email) {
        Company empresa = companyRepository.save(Company.builder().nombre("Empresa " + email).build());
        userRepository.save(User.builder()
                .email(email).nombre("Empleado").contrasena("hash")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build());

        timeEntryService.registerTimeEntry(email, new TimeEntryRequest(TimeEntryAction.INICIO));
        timeEntryService.registerTimeEntry(email, new TimeEntryRequest(TimeEntryAction.PAUSA_INICIO));
        timeEntryService.registerTimeEntry(email, new TimeEntryRequest(TimeEntryAction.PAUSA_FIN));
        timeEntryService.registerTimeEntry(email, new TimeEntryRequest(TimeEntryAction.FIN));
    }

    /**
     * Manipula la tabla saltándose el trigger append-only, como haría quien
     * entrara a la base por debajo de la aplicación. Va con el rol dueño, que
     * es el único que puede desactivar un trigger.
     */
    private void manipularSaltandoseElTrigger(String sql) throws Exception {
        try (Connection duenio = DriverManager.getConnection(testUrl, "nxtime", "nxtime");
             Statement statement = duenio.createStatement()) {
            statement.execute("ALTER TABLE auditoria_fichaje DISABLE TRIGGER USER");
            try {
                statement.execute(sql);
            } finally {
                statement.execute("ALTER TABLE auditoria_fichaje ENABLE TRIGGER USER");
            }
        }
    }

    @Test
    @DisplayName("Una traza recién escrita se verifica entera: el hash se puede recalcular")
    void trazaIntacta_seVerifica() {
        jornadaCompleta("intacta@nxtime.test");

        AuditIntegrityResponse resultado = verificador.verificar();

        assertThat(resultado.intacta()).isTrue();
        assertThat(resultado.movimientos()).isGreaterThanOrEqualTo(4);
        // Todas recalculadas: esto es lo que no se podía hacer antes.
        assertThat(resultado.comprobados()).isEqualTo(resultado.movimientos());
        assertThat(resultado.soloEnlace()).isZero();
        assertThat(resultado.primerFallo()).isNull();
    }

    @Test
    @DisplayName("Cambiarle el motivo a una fila por debajo se detecta, y dice cuál")
    void contenidoManipulado_seDetecta() throws Exception {
        jornadaCompleta("manipulada@nxtime.test");
        long id = auditRepository.findAllByOrderByIdAsc().get(1).getId();

        manipularSaltandoseElTrigger(
                "UPDATE auditoria_fichaje SET motivo = 'Lo cambie yo' WHERE id = " + id);

        AuditIntegrityResponse resultado = verificador.verificar();

        assertThat(resultado.intacta()).isFalse();
        assertThat(resultado.primerFallo()).isEqualTo(id);
        assertThat(resultado.motivo()).contains("no coincide con su hash");
    }

    @Test
    @DisplayName("Cambiar las HORAS de un fichaje en la traza también se detecta")
    void horasManipuladas_seDetectan() throws Exception {
        jornadaCompleta("horas@nxtime.test");
        long id = auditRepository.findAllByOrderByIdAsc().get(2).getId();

        // El caso que de verdad importa: alguien retoca las horas guardadas
        // en el JSON del movimiento. Es el dato que mira una inspección.
        manipularSaltandoseElTrigger(
                "UPDATE auditoria_fichaje SET valor_nuevo = jsonb_set(valor_nuevo, '{horaEntrada}', "
                        + "'\"2020-01-01T06:00:00Z\"') WHERE id = " + id);

        AuditIntegrityResponse resultado = verificador.verificar();

        assertThat(resultado.intacta()).isFalse();
        assertThat(resultado.primerFallo()).isEqualTo(id);
    }

    @Test
    @DisplayName("Borrar una fila del medio rompe el enlace, y se detecta en la siguiente")
    void filaBorrada_rompeElEnlace() throws Exception {
        jornadaCompleta("borrada@nxtime.test");
        var filas = auditRepository.findAllByOrderByIdAsc();
        long borrada = filas.get(1).getId();
        long siguiente = filas.get(2).getId();

        manipularSaltandoseElTrigger("DELETE FROM auditoria_fichaje WHERE id = " + borrada);

        AuditIntegrityResponse resultado = verificador.verificar();

        assertThat(resultado.intacta()).isFalse();
        // La fila borrada ya no está: quien lo canta es la que venía detrás.
        assertThat(resultado.primerFallo()).isEqualTo(siguiente);
        assertThat(resultado.motivo()).contains("enlace");
    }

    @Test
    @DisplayName("Las filas viejas no se dan por buenas: se cuentan como no comprobables")
    void filasDeLaVersionUno_seCuentanAparte() throws Exception {
        jornadaCompleta("vieja@nxtime.test");
        long id = auditRepository.findAllByOrderByIdAsc().get(0).getId();

        // Se simula una fila escrita antes del cambio: su hash no se puede
        // recalcular, y decir que está verificada sería mentir.
        manipularSaltandoseElTrigger(
                "UPDATE auditoria_fichaje SET version_hash = 1 WHERE id = " + id);

        AuditIntegrityResponse resultado = verificador.verificar();

        assertThat(resultado.intacta()).as("una fila vieja no es una fila rota").isTrue();
        assertThat(resultado.soloEnlace()).isEqualTo(1);
        assertThat(resultado.comprobados()).isEqualTo(resultado.movimientos() - 1);
    }
}
