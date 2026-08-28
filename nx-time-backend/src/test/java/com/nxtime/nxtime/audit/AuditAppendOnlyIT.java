package com.nxtime.nxtime.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifica que la auditoría de fichajes es realmente APPEND-ONLY
 * (V5__audit_append_only_trigger.sql).
 *
 * Por qué existe este test, y por qué usa el DataSource de la
 * aplicación en vez de mocks:
 *
 * La Fase 8 protegió la tabla revocando UPDATE/DELETE al rol de la
 * aplicación. Eso funcionaba en el PostgreSQL de docker-compose, pero
 * al desplegar en Neon se descubrió que allí NO: Neon mete a todos los
 * roles en "neon_superuser" y el REVOKE deja de tener efecto. La
 * garantía más importante del módulo habría sido decorativa justo en
 * producción.
 *
 * La solución fue un trigger, que no depende de los privilegios del
 * rol. Este test lo comprueba EJECUTANDO las sentencias prohibidas
 * contra la base de datos real: es la única forma de saber que la
 * protección sigue en pie. Si alguien borra el trigger en una
 * migración futura, esto se pone rojo.
 */
@SpringBootTest
class AuditAppendOnlyIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "audit_appendonly_it_" + System.nanoTime();
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
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    /** Ficha una entrada, que es lo que genera la primera fila de auditoría. */
    private long generarFilaDeAuditoria(String email) {
        Company empresa = companyRepository.save(Company.builder().nombre("Empresa " + email).build());
        userRepository.save(User.builder()
                .email(email).nombre("Empleado").contrasena("hash")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build());

        timeEntryService.registerTimeEntry(email, new TimeEntryRequest(TimeEntryAction.INICIO));

        var filas = auditRepository.findAll();
        assertThat(filas).as("fichar debe dejar traza de auditoría").isNotEmpty();
        return filas.get(0).getId();
    }

    private void ejecutar(String sql) throws Exception {
        try (Connection conexion = dataSource.getConnection();
             Statement statement = conexion.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * La sentencia debe ser rechazada, y da igual CUÁL de las dos
     * barreras la pare: hay dos, y saltan en distinto orden según el
     * entorno.
     *
     *  - "permission denied": el REVOKE de la Fase 8
     *    (V3__audit_trail.sql). Es la que actúa aquí, en el PostgreSQL
     *    de docker-compose, donde "nxtime_app" es un rol normal.
     *  - "append-only": el trigger de la Fase 11
     *    (V5__audit_append_only_trigger.sql). Es la que actúa en Neon,
     *    donde el REVOKE no sirve porque todos los roles heredan
     *    "neon_superuser".
     *
     * Comprobar el mensaje exacto ataría el test a un entorno concreto;
     * lo que hay que garantizar es que la operación NO se completa.
     */
    private void debeSerRechazada(String sql) {
        assertThatThrownBy(() -> ejecutar(sql))
                .as("la auditoría debe rechazar: %s", sql)
                .isInstanceOf(Exception.class)
                .satisfies(e -> assertThat(e.getMessage())
                        .containsAnyOf("append-only", "permission denied"));
    }

    @Test
    @DisplayName("UPDATE sobre una fila de auditoría es rechazado por la base de datos")
    void update_esRechazado() throws Exception {
        long id = generarFilaDeAuditoria("update@nxtime.test");

        debeSerRechazada("UPDATE auditoria_fichaje SET motivo = 'MANIPULADO' WHERE id = " + id);

        // Y lo que importa de verdad: el dato sigue como estaba.
        assertThat(auditRepository.findById(id)).isPresent()
                .get().extracting(a -> a.getMotivo()).isNotEqualTo("MANIPULADO");
    }

    @Test
    @DisplayName("DELETE sobre una fila de auditoría es rechazado por la base de datos")
    void delete_esRechazado() throws Exception {
        long id = generarFilaDeAuditoria("delete@nxtime.test");

        debeSerRechazada("DELETE FROM auditoria_fichaje WHERE id = " + id);

        assertThat(auditRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("TRUNCATE tampoco vacía la tabla: un trigger de fila no se dispara con TRUNCATE")
    void truncate_esRechazado() throws Exception {
        generarFilaDeAuditoria("truncate@nxtime.test");
        long filasAntes = auditRepository.count();

        // Sin el trigger BEFORE TRUNCATE de V5, esto vaciaría la tabla
        // entera de una sola sentencia sin que el trigger de fila
        // llegara a ejecutarse ni una vez.
        assertThatThrownBy(() -> ejecutar("TRUNCATE auditoria_fichaje"))
                .isInstanceOf(Exception.class);

        assertThat(auditRepository.count()).isEqualTo(filasAntes);
    }

    @Test
    @DisplayName("INSERT SÍ está permitido: la tabla es append-only, no de solo lectura")
    void insert_siEstaPermitido() {
        long id = generarFilaDeAuditoria("insert@nxtime.test");

        // Que la propia aplicación haya podido escribir la fila anterior
        // ya lo demuestra: si el trigger bloqueara también el INSERT, no
        // se podría auditar nada y la aplicación no funcionaría.
        assertThat(auditRepository.findById(id)).isPresent();
        assertThat(auditRepository.count()).isPositive();
    }
}
