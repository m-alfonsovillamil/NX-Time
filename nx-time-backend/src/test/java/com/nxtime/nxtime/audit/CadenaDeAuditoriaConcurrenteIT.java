package com.nxtime.nxtime.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryAuditRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Comprueba que varios fichajes simultáneos no bifurcan la cadena de hashes.
 *
 * <h2>Qué se está probando</h2>
 *
 * Hasta septiembre de 2026, {@link TimeEntryAuditListener} leía la última fila
 * de la cadena con un {@code SELECT ... ORDER BY id DESC LIMIT 1} sin bloqueo,
 * y su comentario decía que con una sola instancia eso no era un problema
 * real. No era cierto: en READ COMMITTED, dos hilos de Tomcat que atienden dos
 * fichajes a la vez leen los dos la misma "última fila" y anotan el mismo
 * {@code hashAnterior}. El {@code UNIQUE} sobre el hash no lo impide, porque
 * los contenidos difieren y sus hashes también. Lo que queda es una cadena
 * bifurcada y un verificador acusando de manipulación a un registro con valor
 * legal que nadie había tocado.
 *
 * <h2>Por qué contra PostgreSQL real y con hilos de verdad</h2>
 *
 * El arreglo es un advisory lock de PostgreSQL, así que un test con mocks no
 * probaría nada: lo que hay que demostrar es que dos transacciones reales se
 * serializan. {@link TimeEntryAuditListenerTest} cubre lo otro --que el lock se
 * pide antes de leer--, que sí es comprobable sin base de datos.
 *
 * <h2>Cómo saber que este test sirve</h2>
 *
 * Comprobado al escribirlo, comentando la llamada a {@code bloquearCadena} en
 * {@link TimeEntryAuditListener}: el test se pone rojo con
 *
 * <pre>
 * duplicate key value violates unique constraint "uq_auditoria_hash_anterior"
 *   Detail: Key (hash_anterior)=(79a30f35...) already exists.
 * </pre>
 *
 * es decir, dos fichajes a la vez <b>sí</b> anotan el mismo hashAnterior, en
 * una sola instancia y sin forzar nada. Conviene rehacer esa comprobación si
 * alguien toca esta zona: un test de concurrencia que ya no detecta la carrera
 * es peor que no tenerlo, porque da confianza sin darla.
 */
@SpringBootTest
class CadenaDeAuditoriaConcurrenteIT {

    /**
     * Suficientes para que se pisen de verdad. Con menos, el test pasa por
     * suerte tan a menudo que dejaría de detectar la regresión.
     */
    private static final int FICHAJES_A_LA_VEZ = 8;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "audit_concurrente_it_" + System.nanoTime();
        String adminUrl = "jdbc:postgresql://localhost:5433/nxtime";
        try (Connection admin = DriverManager.getConnection(adminUrl, "nxtime", "nxtime");
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
    private DataSource dataSource;
    @Autowired
    private TimeEntryService timeEntryService;
    @Autowired
    private TimeEntryAuditRepository auditRepository;
    @Autowired
    private VerificadorDeAuditoria verificador;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    /** Empleados de empresas distintas: la cadena de hashes es global, no por empresa. */
    private List<String> darDeAltaEmpleados(int cuantos) {
        List<String> correos = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            Company empresa = companyRepository.save(
                    Company.builder().nombre("Empresa concurrente " + i + "-" + System.nanoTime()).build());
            String email = "concurrente" + i + "@nxtime.test";
            userRepository.save(User.builder()
                    .email(email).nombre("Empleado " + i).contrasena("hash")
                    .rol(Role.EMPLEADO).empresa(empresa).activo(true).build());
            correos.add(email);
        }
        return correos;
    }

    /**
     * Lanza un fichaje por hilo y los suelta todos a la vez.
     *
     * Cada llamada abre su propia transacción --{@code registerTimeEntry} es
     * {@code @Transactional}--, que es justo el escenario que se quiere: no dos
     * eventos dentro de una transacción, sino dos transacciones compitiendo.
     *
     * @return los fallos ocurridos, vacío si todo fue bien
     */
    private List<Throwable> ficharTodosALaVez(List<String> correos) throws InterruptedException {
        List<Throwable> fallos = new CopyOnWriteArrayList<>();
        CountDownLatch enPosicion = new CountDownLatch(correos.size());
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(correos.size());

        ExecutorService hilos = Executors.newFixedThreadPool(correos.size());
        try {
            for (String email : correos) {
                hilos.submit(() -> {
                    try {
                        enPosicion.countDown();
                        salida.await();
                        timeEntryService.registerTimeEntry(email, new TimeEntryRequest(TimeEntryAction.INICIO));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        fallos.add(e);
                    } catch (Throwable e) {
                        fallos.add(e);
                    } finally {
                        terminados.countDown();
                    }
                });
            }

            assertThat(enPosicion.await(30, TimeUnit.SECONDS)).as("los hilos deben llegar a la línea de salida").isTrue();
            salida.countDown();
            assertThat(terminados.await(60, TimeUnit.SECONDS)).as("los fichajes deben terminar").isTrue();
        } finally {
            hilos.shutdownNow();
        }
        return fallos;
    }

    /** Filas distintas que dicen ir detrás de la misma fila. Cero o la cadena está bifurcada. */
    private long bifurcaciones() throws Exception {
        String sql = """
                SELECT COUNT(*) FROM (
                    SELECT hash_anterior FROM auditoria_fichaje
                    WHERE hash_anterior IS NOT NULL
                    GROUP BY hash_anterior HAVING COUNT(*) > 1
                ) duplicados
                """;
        try (Connection conexion = dataSource.getConnection();
             Statement statement = conexion.createStatement();
             ResultSet resultado = statement.executeQuery(sql)) {
            resultado.next();
            return resultado.getLong(1);
        }
    }

    @Test
    @DisplayName("Ocho fichajes simultáneos dejan la cadena intacta y sin bifurcar")
    void fichajesSimultaneos_cadenaIntacta() throws Exception {
        List<String> correos = darDeAltaEmpleados(FICHAJES_A_LA_VEZ);

        List<Throwable> fallos = ficharTodosALaVez(correos);

        // 1. Ningún fichaje se pierde. Sin el advisory lock, el índice único de
        //    V27 haría fallar a alguno: la protección de datos funcionaría,
        //    pero a costa de que a alguien no le cuente la jornada.
        assertThat(fallos)
                .as("ningún fichaje debe fallar: el encadenamiento tiene que serializarse, no chocar")
                .isEmpty();

        // 2. Una fila de auditoría por fichaje, ni una menos.
        assertThat(auditRepository.count())
                .as("cada fichaje deja su traza")
                .isEqualTo(FICHAJES_A_LA_VEZ);

        // 3. Nadie comparte hashAnterior con nadie.
        assertThat(bifurcaciones())
                .as("dos filas no pueden ir detrás de la misma fila")
                .isZero();

        // 4. Y lo que de verdad importa: el verificador no acusa a nadie.
        var integridad = verificador.verificar();
        assertThat(integridad.intacta())
                .as("la cadena debe estar intacta (primer fallo: %s, motivo: %s)",
                        integridad.primerFallo(), integridad.motivo())
                .isTrue();
        assertThat(integridad.movimientos()).isEqualTo(FICHAJES_A_LA_VEZ);
    }
}
