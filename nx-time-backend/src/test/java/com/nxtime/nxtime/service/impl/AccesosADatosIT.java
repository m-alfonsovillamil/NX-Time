package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.ComplaintCategory;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CreateComplaintRequest;
import com.nxtime.nxtime.dto.DataAccessResponse;
import com.nxtime.nxtime.dto.UpdateCompanySettingsRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.CompanySettingsService;
import com.nxtime.nxtime.service.ComplaintService;
import com.nxtime.nxtime.service.DataDeletionService;
import com.nxtime.nxtime.service.EmployeeProfileService;
import com.nxtime.nxtime.service.ReportService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El registro de accesos, el aviso de privacidad y el plazo de los borrados
 * (plan J, fase J5; ADR 047), contra PostgreSQL real y como {@code nxtime_app}.
 *
 * Lo que solo se ve aquí, y no con mocks: que anotar funciona dentro de las
 * transacciones de solo lectura de los servicios (PostgreSQL no deja insertar
 * en ellas), y que el trigger de V44 no deja cambiar ni borrar lo anotado.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class AccesosADatosIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "accesos_it_" + System.nanoTime();
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

    @Autowired private RegistroDeAccesos accesos;
    @Autowired private ConservacionDeDatos conservacion;
    @Autowired private AvisoDePrivacidad aviso;
    @Autowired private ReportService informes;
    @Autowired private EmployeeProfileService perfiles;
    @Autowired private ComplaintService denuncias;
    @Autowired private DataDeletionService borrados;
    @Autowired private CompanySettingsService ajustes;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JdbcTemplate jdbc;

    private Company empresa;
    private User admin;
    private User rrhh;
    private User ana;

    private User persona(Company deEmpresa, String nombre, Role rol) {
        return userRepository.save(User.builder().nombre(nombre).apellidos("Pruebas")
                .email(nombre.toLowerCase() + System.nanoTime() + "@accesos.test").contrasena("x")
                .rol(rol).empresa(deEmpresa).activo(true).build());
    }

    @BeforeEach
    void empresa() {
        empresa = companyRepository.save(Company.builder().nombre("Accesos " + System.nanoTime()).build());
        admin = persona(empresa, "Admin", Role.ADMIN);
        rrhh = persona(empresa, "Rrhh", Role.RRHH);
        ana = persona(empresa, "Ana", Role.EMPLEADO);
    }

    private List<DataAccessResponse> anotados() {
        return accesos.deLaEmpresa(admin, null);
    }

    @Test
    @DisplayName("Lo ajeno queda anotado, también desde las transacciones de solo lectura; lo propio, no")
    void loAjenoSeAnota() {
        YearMonth mes = YearMonth.now(ZoneId.of("Europe/Madrid"));

        informes.informeDeEmpleado(rrhh.getEmail(), ana.getId(), mes);
        informes.informeDeEmpleado(ana.getEmail(), ana.getId(), mes);
        informes.informeDeEmpresa(rrhh.getEmail(), mes);
        perfiles.getProfile(ana.getId(), rrhh);
        perfiles.getProfile(rrhh.getId(), rrhh);

        denuncias.presentar(new CreateComplaintRequest(ComplaintCategory.ACOSO, "Hechos", false), ana);
        long expediente = jdbc.queryForObject("SELECT id FROM denuncias WHERE empresa_id = ?", Long.class, empresa.getId());
        denuncias.detalle(expediente, admin);

        List<DataAccessResponse> hechos = anotados();
        assertThat(hechos).extracting(DataAccessResponse::que)
                .containsExactlyInAnyOrder("INFORME_MENSUAL", "INFORME_EMPRESA", "PERFIL", "DENUNCIA");
        DataAccessResponse informe = hechos.stream().filter(a -> a.que().equals("INFORME_MENSUAL")).findFirst().orElseThrow();
        assertThat(informe.quien()).isEqualTo("Rrhh Pruebas");
        assertThat(informe.sobre()).isEqualTo("Ana Pruebas");
        assertThat(informe.detalle()).isEqualTo("Registro de " + mes);
        // De una denuncia no se anota de quién es: quién la puso no queda en ninguna parte.
        DataAccessResponse denuncia = hechos.stream().filter(a -> a.que().equals("DENUNCIA")).findFirst().orElseThrow();
        assertThat(denuncia.sobreId()).isNull();
        assertThat(denuncia.detalle()).isEqualTo("Expediente " + expediente);

        assertThat(accesos.deLaEmpresa(admin, ana.getId())).extracting(DataAccessResponse::que)
                .containsExactlyInAnyOrder("INFORME_MENSUAL", "PERFIL");

        // Otra empresa no ve nada de esta.
        Company otra = companyRepository.save(Company.builder().nombre("Otra " + System.nanoTime()).build());
        assertThat(accesos.deLaEmpresa(persona(otra, "Ajeno", Role.ADMIN), null)).isEmpty();
    }

    @Test
    @DisplayName("Lo anotado no se puede cambiar ni borrar hasta que pasan dos años o desaparece la empresa")
    void soloInsercion() {
        accesos.anotar(rrhh, com.nxtime.nxtime.domain.DatoConsultado.PERFIL, ana.getId(), null);
        long id = jdbc.queryForObject("SELECT max(id) FROM accesos_a_datos WHERE empresa_id = ?", Long.class, empresa.getId());

        assertThatThrownBy(() -> jdbc.update("UPDATE accesos_a_datos SET quien_id = 0 WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM accesos_a_datos WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE accesos_a_datos"))
                .isInstanceOf(DataAccessException.class);

        // Uno de hace tres años sí se va con la tarea de la noche, y el de hoy se queda.
        jdbc.update("INSERT INTO accesos_a_datos (empresa_id, quien_id, que, en) VALUES (?, ?, 'PERFIL', ?)",
                empresa.getId(), rrhh.getId(), Timestamp.from(Instant.now().minus(3 * 366, ChronoUnit.DAYS)));
        assertThat(conservacion.aplicar(Instant.now())).containsEntry("registroDeAccesos", 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accesos_a_datos WHERE empresa_id = ?",
                Integer.class, empresa.getId())).isEqualTo(1);

        // Y los de una empresa que ya no existe, también.
        Company efimera = companyRepository.save(Company.builder().nombre("Efímera " + System.nanoTime()).build());
        jdbc.update("INSERT INTO accesos_a_datos (empresa_id, quien_id, que) VALUES (?, 1, 'PERFIL')", efimera.getId());
        companyRepository.deleteById(efimera.getId());
        assertThat(jdbc.update("DELETE FROM accesos_a_datos WHERE empresa_id = ?", efimera.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("El aviso de privacidad sale una vez, con el contacto que puso el ADMIN")
    void avisoDePrivacidad() {
        assertThat(aviso.leer(ana).visto()).isFalse();
        assertThat(aviso.leer(ana).contacto()).isNull();

        ajustes.guardar(new UpdateCompanySettingsRequest(empresa.getNombre(), empresa.getZonaHoraria(),
                " privacidad@empresa.es "), admin);
        assertThat(aviso.leer(ana).contacto()).isEqualTo("privacidad@empresa.es");
        assertThat(aviso.leer(ana).empresa()).isEqualTo(empresa.getNombre());

        aviso.marcarVisto(ana);
        Instant primera = userRepository.avisoDePrivacidadVistoEn(ana.getId()).orElseThrow();
        aviso.marcarVisto(ana);
        assertThat(userRepository.avisoDePrivacidadVistoEn(ana.getId())).contains(primera);
        assertThat(aviso.leer(ana).visto()).isTrue();
        // Guardar la entidad con una copia vieja no lo devuelve a «sin ver».
        userRepository.save(userRepository.findById(ana.getId()).orElseThrow());
        assertThat(aviso.leer(ana).visto()).isTrue();
    }

    @Test
    @DisplayName("El contacto de privacidad es un correo o un enlace https, o nada")
    void contactoValido() {
        for (String malo : List.of("javascript:alert(1)", "http://empresa.es/privacidad", "no es nada", "https://")) {
            assertThatThrownBy(() -> ajustes.guardar(new UpdateCompanySettingsRequest(
                    empresa.getNombre(), empresa.getZonaHoraria(), malo), admin))
                    .as(malo).isInstanceOf(BusinessException.class);
        }
        assertThat(ajustes.guardar(new UpdateCompanySettingsRequest(empresa.getNombre(), empresa.getZonaHoraria(),
                "https://empresa.es/privacidad"), admin).contactoPrivacidad()).isEqualTo("https://empresa.es/privacidad");
        assertThat(ajustes.guardar(new UpdateCompanySettingsRequest(empresa.getNombre(), empresa.getZonaHoraria(),
                "  "), admin).contactoPrivacidad()).isNull();
    }

    @Test
    @DisplayName("Una solicitud de borrado lleva la cuenta del mes que da el RGPD para responder")
    void plazoDeUnMes() {
        long dias = borrados.solicitar(ana, null).diasParaResponder();
        LocalDate hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));
        assertThat(dias).isEqualTo(ChronoUnit.DAYS.between(hoy, hoy.plusMonths(1)));
        assertThat(borrados.pendientes(admin)).singleElement()
                .satisfies(s -> assertThat(s.diasParaResponder()).isEqualTo(dias));
    }
}
