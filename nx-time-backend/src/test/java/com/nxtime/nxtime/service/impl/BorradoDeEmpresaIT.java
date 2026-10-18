package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.audit.VerificadorDeAuditoria;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.ComplaintCategory;
import com.nxtime.nxtime.domain.DatoConsultado;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuditIntegrityResponse;
import com.nxtime.nxtime.dto.CreateComplaintRequest;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.AuditCheckpointRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.ComplaintService;
import com.nxtime.nxtime.service.TimeEntryService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * La baja de una empresa con borrado (V46, ADR 050), entera y contra
 * PostgreSQL real, con la aplicación conectada como {@code nxtime_app}: es ese
 * rol el que no tiene que poder borrar la auditoría por su cuenta.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class BorradoDeEmpresaIT {

    private static String testUrl;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "borrado_empresa_it_" + System.nanoTime();
        try (Connection admin = DriverManager.getConnection("jdbc:postgresql://localhost:5433/nxtime", "nxtime", "nxtime");
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
        registry.add("application.auditoria.vigencia-del-recorrido", () -> "0s");
    }

    @Autowired private BajaDeEmpresa baja;
    @Autowired private ConservacionDeDatos conservacion;
    @Autowired private VerificadorDeAuditoria verificador;
    @Autowired private AuditCheckpointRepository puntos;
    @Autowired private TimeEntryService fichajes;
    @Autowired private ComplaintService denuncias;
    @Autowired private RegistroDeAccesos accesos;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JdbcTemplate jdbc;

    private User persona(Company empresa, String nombre, Role rol) {
        return userRepository.save(User.builder().nombre(nombre).apellidos("Pruebas")
                .email(nombre.toLowerCase() + System.nanoTime() + "@baja.test").contrasena("x")
                .rol(rol).empresa(empresa).activo(true).build());
    }

    private void jornada(User quien) {
        fichajes.registerTimeEntry(quien.getEmail(), new TimeEntryRequest(TimeEntryAction.INICIO));
        fichajes.registerTimeEntry(quien.getEmail(), new TimeEntryRequest(TimeEntryAction.FIN));
    }

    /** Como el dueño de la base y sin el trigger: para dejar la traza como estaba antes de V46. */
    private static void comoDuenio(String sql) throws Exception {
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

    private int cuantas(String sql, Object... argumentos) {
        Integer n = jdbc.queryForObject(sql, Integer.class, argumentos);
        return n == null ? 0 : n;
    }

    /** Lo que queda de una empresa y de su gente en cualquier tabla, menos las constancias que tienen que quedar. */
    private List<String> loQueQuedaDe(long empresaId, List<Long> usuarios) {
        List<String> tablas = jdbc.queryForList("""
                SELECT DISTINCT table_name || '.' || column_name FROM information_schema.columns
                 WHERE table_schema = 'public' AND column_name IN ('empresa_id', 'usuario_id')
                   AND table_name NOT IN ('acciones_de_plataforma', 'empresas_borradas', 'eslabones_retirados')
                """, String.class);
        String lista = usuarios.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0");
        return tablas.stream().filter(t -> {
            String tabla = t.substring(0, t.indexOf('.'));
            String columna = t.substring(t.indexOf('.') + 1);
            String valor = columna.equals("empresa_id") ? String.valueOf(empresaId) : lista;
            return cuantas("SELECT count(*) FROM " + tabla + " WHERE " + columna + " IN (" + valor + ")") > 0;
        }).toList();
    }

    @Test
    @DisplayName("Pedir la baja exige el nombre de la empresa, y se puede cancelar")
    void pedirYCancelar() {
        Company empresa = companyRepository.save(Company.builder().nombre("Talleres Baja " + System.nanoTime()).build());
        User admin = persona(empresa, "Admin", Role.ADMIN);

        assertThatThrownBy(() -> baja.solicitar(admin, "Otra empresa")).isInstanceOf(BusinessException.class);
        var pedida = baja.solicitar(admin, "  " + empresa.getNombre().toUpperCase() + " ");
        assertThat(pedida.solicitadaEn()).isNotNull();
        assertThat(Duration.between(pedida.solicitadaEn(), pedida.borradoDesde())).isEqualTo(Duration.ofDays(30));
        assertThatThrownBy(() -> baja.solicitar(admin, empresa.getNombre())).isInstanceOf(BusinessException.class);

        assertThat(baja.cancelar(admin).solicitadaEn()).isNull();
        assertThat(companyRepository.findById(empresa.getId()).orElseThrow().getBajaSolicitadaEn()).isNull();
    }

    @Test
    @DisplayName("A los 30 días se borra todo de la empresa, y la traza de las demás sigue intacta")
    void seBorraTodoYLaTrazaSigueIntacta() throws Exception {
        Company seVa = companyRepository.save(Company.builder().nombre("Se va " + System.nanoTime()).build());
        Company seQueda = companyRepository.save(Company.builder().nombre("Se queda " + System.nanoTime()).build());
        User adminQueSeVa = persona(seVa, "Admin", Role.ADMIN);
        User anaQueSeVa = persona(seVa, "Ana", Role.EMPLEADO);
        User luisQueSeQueda = persona(seQueda, "Luis", Role.EMPLEADO);

        // El tramo común de antes de V46, con filas de las dos intercaladas, y
        // la última de la que se va: así el punto de control del tramo común
        // apuntará a una fila que se convertirá en lápida.
        jornada(luisQueSeQueda);
        jornada(anaQueSeVa);
        comoDuenio("""
                UPDATE auditoria_fichaje a
                   SET version_hash = 1, empresa_id = NULL, hash_anterior = p.anterior
                  FROM (SELECT id, lag(hash) OVER (ORDER BY id) AS anterior FROM auditoria_fichaje) p
                 WHERE a.id = p.id
                """);
        // Y ya con V46, cada una con su cadena.
        jornada(anaQueSeVa);
        jornada(luisQueSeQueda);
        assertThat(verificador.verificarLoNuevoYAnotar().intacta()).isTrue();
        long hastaDelComun = puntos.findTopByEmpresaIdIsNullOrderByHastaIdDesc().orElseThrow().getHastaId();
        assertThat(cuantas("SELECT count(*) FROM auditoria_fichaje a JOIN registros r ON r.id = a.registro_id "
                + "WHERE a.id = ? AND r.empresa_id = ?", hastaDelComun, seVa.getId())).isEqualTo(1);

        // Un poco de todo lo demás.
        denuncias.presentar(new CreateComplaintRequest(ComplaintCategory.ACOSO, "Hechos", false), anaQueSeVa);
        accesos.anotar(adminQueSeVa, DatoConsultado.PERFIL, anaQueSeVa.getId(), null);
        jdbc.update("INSERT INTO departamentos (empresa_id, nombre) VALUES (?, 'Taller')", seVa.getId());
        jdbc.update("INSERT INTO avisos (empresa_id, destinatario_id, tipo, titulo, cuerpo, creado_en) VALUES (?, ?, 'BIENVENIDA', 'Hola', 'x', now())",
                seVa.getId(), anaQueSeVa.getId());
        jdbc.update("INSERT INTO dispositivos_push (usuario_id, plataforma, token, registrado_en, visto_en) VALUES (?, 'WEB', ?, now(), now())",
                anaQueSeVa.getId(), "t" + System.nanoTime());
        jdbc.update("INSERT INTO codigos_acceso (usuario_id, tipo, codigo_hash, creado_en, expira_en) VALUES (?, 'ALTA', 'x', now(), now() + interval '1 hour')",
                anaQueSeVa.getId());
        jdbc.update("INSERT INTO refresh_tokens (token_hash, usuario_id, familia, origen, expira_en, revocado, creado_en) "
                + "VALUES (?, ?, ?, 'WEB', now(), false, now())", "h" + System.nanoTime(), anaQueSeVa.getId(), UUID.randomUUID());
        jdbc.update("""
                INSERT INTO firmas_mensuales (empresa_id, usuario_id, anio, mes, hash, version_huella, jornadas, segundos_netos, estado, firmada_en)
                VALUES (?, ?, 2026, 9, ?, 1, 1, 3600, 'VIGENTE', now())
                """, seVa.getId(), anaQueSeVa.getId(), "b".repeat(64));
        int deLuisAntes = cuantas("SELECT count(*) FROM auditoria_fichaje WHERE usuario_id = ?", luisQueSeQueda.getId());

        baja.solicitar(userRepository.findById(adminQueSeVa.getId()).orElseThrow(), seVa.getNombre());

        // Antes de los 30 días, ni la función ni la aplicación por su cuenta.
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT borrar_empresa(?)", Long.class, seVa.getId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("SELECT set_config('nxtime.borrando_empresa', '" + seVa.getId()
                + "', false); DELETE FROM auditoria_fichaje WHERE empresa_id = " + seVa.getId()))
                .isInstanceOf(DataAccessException.class);
        // Ni siquiera alguien con privilegio de borrar (el dueño, sin pasar por
        // la función): en Neon los REVOKE no bastan, y lo que protege es el
        // trigger, que solo deja pasar a quien está DENTRO de borrar_empresa.
        try (Connection duenio = DriverManager.getConnection(testUrl, "nxtime", "nxtime");
             Statement statement = duenio.createStatement()) {
            statement.execute("SELECT set_config('nxtime.borrando_empresa', '" + seVa.getId() + "', false)");
            assertThatThrownBy(() -> statement.execute("DELETE FROM auditoria_fichaje WHERE empresa_id = " + seVa.getId()))
                    .hasMessageContaining("append-only");
        }
        assertThat(conservacion.aplicar(Instant.now())).containsEntry("empresasBorradas", 0);

        jdbc.update("UPDATE empresas SET baja_solicitada_en = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(31))), seVa.getId());
        assertThat(conservacion.aplicar(Instant.now())).containsEntry("empresasBorradas", 1);

        // De la que se fue no queda nada en ninguna tabla...
        assertThat(companyRepository.findById(seVa.getId())).isEmpty();
        assertThat(loQueQuedaDe(seVa.getId(), List.of(adminQueSeVa.getId(), anaQueSeVa.getId()))).isEmpty();
        // ...salvo que existió, y las lápidas de su tramo común.
        assertThat(cuantas("SELECT count(*) FROM empresas_borradas WHERE empresa_id = ?", seVa.getId())).isEqualTo(1);
        assertThat(cuantas("SELECT count(*) FROM eslabones_retirados WHERE empresa_id = ?", seVa.getId())).isEqualTo(2);

        // La que se queda no ha perdido nada, y la traza entera sigue intacta:
        // el tramo común se recorre con las lápidas y su punto de control vale.
        assertThat(cuantas("SELECT count(*) FROM auditoria_fichaje WHERE usuario_id = ?", luisQueSeQueda.getId()))
                .isEqualTo(deLuisAntes);
        AuditIntegrityResponse todo = verificador.verificar();
        assertThat(todo.intacta()).isTrue();
        assertThat(todo.soloEnlace()).as("las de Luis del tramo común y las dos lápidas").isEqualTo(4);
        assertThat(verificador.verificarPara(seQueda.getId()).intacta()).isTrue();
        assertThat(verificador.verificarPara(seQueda.getId()).movimientos()).isEqualTo(4);
        // La nocturna cuenta lo que hay de verdad: las lápidas ya no son «comprobadas».
        AuditIntegrityResponse nocturna = verificador.verificarLoNuevoYAnotar();
        assertThat(nocturna.intacta()).isTrue();
        assertThat(nocturna.comprobados()).isEqualTo(todo.comprobados());
        assertThat(nocturna.soloEnlace()).isEqualTo(todo.soloEnlace());
        assertThat(verificador.verificarTodoYAnotar().intacta()).isTrue();

        // Y las constancias no se pueden tocar.
        assertThatThrownBy(() -> jdbc.update("DELETE FROM empresas_borradas")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM eslabones_retirados")).isInstanceOf(DataAccessException.class);
    }
}
