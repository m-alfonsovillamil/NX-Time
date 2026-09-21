package com.nxtime.nxtime.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.TimeEntryAudit;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
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

        // Bloques de 2 filas: así una sola jornada (4 movimientos) ya obliga al
        // verificador a cruzar varios bloques y a soltar la sesión entre ellos.
        // Con el valor de producción (1000) haría falta sembrar más de mil
        // filas para ejercitar el mismo código.
        registry.add("application.auditoria.filas-por-bloque", () -> 2);
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
        // Los puntos de control también: uno que apunte a una fila ya truncada
        // haría que la verificación incremental arrancara desde un sitio que
        // no existe, y el fallo saldría en el test de al lado.
        manipularSaltandoseElTrigger("DELETE FROM puntos_control_auditoria");
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
    /**
     * La traza entera, para poder señalar una fila concreta y manipularla.
     *
     * Lo hace el test y no el repositorio: el {@code findAllByOrderByIdAsc()}
     * que había ahí era justo el que traía a memoria cuatro años de traza de
     * todas las empresas, y devolverlo «solo para los tests» es la forma
     * habitual de que vuelva a usarse en producción. Aquí son cuatro filas.
     */
    private List<TimeEntryAudit> todasLasFilas() {
        return auditRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

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
        long id = todasLasFilas().get(1).getId();

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
        long id = todasLasFilas().get(2).getId();

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
        var filas = todasLasFilas();
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
        long id = todasLasFilas().get(0).getId();

        // Se simula una fila escrita antes del cambio: su hash no se puede
        // recalcular, y decir que está verificada sería mentir.
        manipularSaltandoseElTrigger(
                "UPDATE auditoria_fichaje SET version_hash = 1 WHERE id = " + id);

        AuditIntegrityResponse resultado = verificador.verificar();

        assertThat(resultado.intacta()).as("una fila vieja no es una fila rota").isTrue();
        assertThat(resultado.soloEnlace()).isEqualTo(1);
        assertThat(resultado.comprobados()).isEqualTo(resultado.movimientos() - 1);
    }

    // ------------------------------------------------------------------
    // Recorrido por bloques y puntos de control (Fase A4)
    // ------------------------------------------------------------------

    /**
     * El tamaño de bloque está puesto a 2 en {@link #datasourceProperties}, así
     * que una jornada completa (4 movimientos) ya cruza tres consultas. Sin
     * bajarlo haría falta sembrar más de mil filas para probar lo mismo, y un
     * recorrido por bloques que solo se prueba con un bloque no prueba nada.
     */
    @Test
    @DisplayName("La traza se verifica igual aunque haya que recorrerla en varios bloques")
    void variosBloques_mismoResultado() {
        jornadaCompleta("bloques1@nxtime.test");
        jornadaCompleta("bloques2@nxtime.test");

        AuditIntegrityResponse resultado = verificador.verificar();

        assertThat(resultado.intacta()).isTrue();
        assertThat(resultado.movimientos()).isEqualTo(8);
        assertThat(resultado.comprobados()).isEqualTo(8);
    }

    @Test
    @DisplayName("Una fila manipulada en un bloque posterior al primero también se detecta")
    void manipulacionEnUnBloquePosterior_seDetecta() throws Exception {
        jornadaCompleta("tardia@nxtime.test");
        jornadaCompleta("tardia2@nxtime.test");
        var filas = todasLasFilas();
        // La séptima de ocho: con bloques de 2, cae en el cuarto bloque, así
        // que el fallo tiene que sobrevivir a tres entityManager.clear().
        long id = filas.get(6).getId();

        manipularSaltandoseElTrigger(
                "UPDATE auditoria_fichaje SET motivo = 'Lo cambie yo' WHERE id = " + id);

        AuditIntegrityResponse resultado = verificador.verificar();

        assertThat(resultado.intacta()).isFalse();
        assertThat(resultado.primerFallo()).isEqualTo(id);
        // Y las seis anteriores sí quedan contadas: el informe dice dónde
        // empezó el problema, no "no sé nada".
        assertThat(resultado.movimientos()).isEqualTo(6);
    }

    @Test
    @DisplayName("La verificación nocturna deja un punto de control con el total y el hash")
    void verificacionNocturna_dejaPuntoDeControl() {
        jornadaCompleta("punto@nxtime.test");

        AuditIntegrityResponse resultado = verificador.verificarLoNuevoYAnotar();

        assertThat(resultado.intacta()).isTrue();
        var punto = verificador.ultimoPuntoDeControl().orElseThrow();
        var ultima = todasLasFilas().get(3);
        assertThat(punto.getHastaId()).isEqualTo(ultima.getId());
        assertThat(punto.getHash()).isEqualTo(ultima.getHash());
        assertThat(punto.getFilas()).isEqualTo(4);
        assertThat(verificador.movimientosSinRevisar(punto)).isZero();
    }

    /** Lo que hace que la tarea nocturna sea barata: no revisa lo ya revisado. */
    @Test
    @DisplayName("La segunda verificación solo mira lo nuevo, y el total sigue siendo el de toda la traza")
    void segundaVerificacion_esIncrementalPeroSumaElTotal() {
        jornadaCompleta("incremental1@nxtime.test");
        verificador.verificarLoNuevoYAnotar();

        jornadaCompleta("incremental2@nxtime.test");
        AuditIntegrityResponse segunda = verificador.verificarLoNuevoYAnotar();

        assertThat(segunda.intacta()).isTrue();
        // 8 y no 4: el punto de control arrastra las cifras de antes, así que
        // el informe sigue hablando de la traza entera aunque solo se hayan
        // leído las cuatro filas nuevas.
        assertThat(segunda.movimientos()).isEqualTo(8);
        assertThat(verificador.ultimoPuntoDeControl().orElseThrow().getFilas()).isEqualTo(8);
    }

    @Test
    @DisplayName("Si la cadena está rota NO se anota punto de control: no se da por bueno un tramo malo")
    void cadenaRota_noAnotaPuntoDeControl() throws Exception {
        jornadaCompleta("rota@nxtime.test");
        long id = todasLasFilas().get(1).getId();
        manipularSaltandoseElTrigger(
                "UPDATE auditoria_fichaje SET motivo = 'Lo cambie yo' WHERE id = " + id);

        AuditIntegrityResponse resultado = verificador.verificarLoNuevoYAnotar();

        assertThat(resultado.intacta()).isFalse();
        // Si se anotara, la noche siguiente empezaría después del tramo malo y
        // la manipulación quedaría tapada para siempre.
        assertThat(verificador.ultimoPuntoDeControl()).isEmpty();
    }

    @Test
    @DisplayName("Verificar dos veces sin movimientos nuevos no duplica el punto de control")
    void sinMovimientosNuevos_noAnotaOtroPunto() {
        jornadaCompleta("repetida@nxtime.test");
        verificador.verificarLoNuevoYAnotar();
        long primero = verificador.ultimoPuntoDeControl().orElseThrow().getId();

        // Sin esto chocaría con uq_punto_control_hasta, y además no diría nada
        // que no dijera ya el anterior.
        verificador.verificarLoNuevoYAnotar();

        assertThat(verificador.ultimoPuntoDeControl().orElseThrow().getId()).isEqualTo(primero);
    }
}
