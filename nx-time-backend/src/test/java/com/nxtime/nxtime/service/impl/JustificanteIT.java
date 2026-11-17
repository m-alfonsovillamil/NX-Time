package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.AbsenceRequest;
import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.JustificanteResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.ResourceNotFoundException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.AbsenceRequestRepository;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.JustificanteService;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El justificante de una ausencia (ADR 062): un dato de salud. Apagado por
 * defecto, solo lo suben la persona y lo ven ella y RRHH/ADMIN, y cada descarga
 * de otra persona queda anotada.
 *
 * <p>Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class JustificanteIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "justificante_it_" + System.nanoTime();
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

    /** Lo justo para que el detector lo tome por un PDF. */
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AbsenceRequestRepository absenceRequestRepository;
    @Autowired private JustificanteService justificantes;
    @Autowired private JdbcTemplate jdbc;

    private Company empresa;
    private User ana;
    private User gestor;
    private User rrhh;
    private AbsenceRequest baja;

    @BeforeEach
    void preparar() {
        empresa = companyRepository.save(Company.builder().nombre("Justificantes " + System.nanoTime()).build());
        ana = persona(empresa, "Ana", Role.EMPLEADO);
        gestor = persona(empresa, "Gema", Role.GESTOR);
        rrhh = persona(empresa, "Rita", Role.RRHH);
        baja = absenceRequestRepository.save(AbsenceRequest.builder()
                .usuario(ana).empresa(empresa).tipo(AbsenceType.MEDICO)
                .fechaInicio(LocalDate.of(2026, 11, 2)).fechaFin(LocalDate.of(2026, 11, 4)).build());
    }

    private User persona(Company deEmpresa, String nombre, Role rol) {
        return userRepository.save(User.builder().nombre(nombre).apellidos("Pruebas")
                .email(nombre.toLowerCase() + "." + System.nanoTime() + "@nxtime.test")
                .contrasena("x").rol(rol).empresa(deEmpresa).build());
    }

    private static MockMultipartFile fichero(String nombre, byte[] bytes) {
        return new MockMultipartFile("fichero", nombre, "application/octet-stream", bytes);
    }

    private void activar() {
        justificantes.activar(true, ana);
    }

    @Test
    @DisplayName("Apagado por defecto: no se puede adjuntar hasta que la empresa lo activa")
    void apagadoPorDefecto() {
        assertThat(justificantes.de(List.of(baja.getId()), ana).activo()).isFalse();
        assertThatThrownBy(() -> justificantes.subir(baja.getId(), fichero("parte.pdf", PDF), ana))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("La persona lo sube y lo ve; RRHH lo ve y queda anotado; el gestor ni sabe que existe")
    void quienLoVe() {
        activar();
        JustificanteResponse subido = justificantes.subir(baja.getId(), fichero("C:\\fakepath\\parte.pdf", PDF), ana);
        assertThat(subido.nombre()).isEqualTo("parte.pdf");
        assertThat(subido.mime()).isEqualTo("application/pdf");

        assertThat(justificantes.de(List.of(baja.getId()), ana).justificantes()).hasSize(1);
        assertThat(justificantes.de(List.of(baja.getId()), rrhh).justificantes()).hasSize(1);
        assertThat(justificantes.de(List.of(baja.getId()), gestor).justificantes()).isEmpty();

        assertThat(justificantes.descargar(baja.getId(), ana).contenido()).isEqualTo(PDF);
        assertThat(anotados()).isZero();
        assertThat(justificantes.descargar(baja.getId(), rrhh).contenido()).isEqualTo(PDF);
        assertThat(anotados()).isEqualTo(1);

        assertThatThrownBy(() -> justificantes.descargar(baja.getId(), gestor))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("Solo la persona lo sube o lo quita, y nadie de otra empresa llega a él")
    void soloLaPersona() {
        activar();
        assertThatThrownBy(() -> justificantes.subir(baja.getId(), fichero("parte.pdf", PDF), rrhh))
                .isInstanceOf(TenantAccessException.class);
        justificantes.subir(baja.getId(), fichero("parte.pdf", PDF), ana);
        assertThatThrownBy(() -> justificantes.borrar(baja.getId(), rrhh)).isInstanceOf(TenantAccessException.class);

        Company otra = companyRepository.save(Company.builder().nombre("Otra " + System.nanoTime()).build());
        User rrhhDeOtra = persona(otra, "Olga", Role.RRHH);
        assertThatThrownBy(() -> justificantes.descargar(baja.getId(), rrhhDeOtra))
                .isInstanceOf(TenantAccessException.class);
        assertThat(justificantes.de(List.of(baja.getId()), rrhhDeOtra).justificantes()).isEmpty();

        justificantes.borrar(baja.getId(), ana);
        assertThatThrownBy(() -> justificantes.descargar(baja.getId(), ana))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Se mira el contenido, no la extensión; y subir otro reemplaza el anterior")
    void tipoYReemplazo() {
        activar();
        assertThatThrownBy(() -> justificantes.subir(baja.getId(),
                fichero("parte.pdf", "MZ no soy un pdf".getBytes(StandardCharsets.US_ASCII)), ana))
                .isInstanceOf(BusinessException.class);
        justificantes.subir(baja.getId(), fichero("uno.pdf", PDF), ana);
        justificantes.subir(baja.getId(), fichero("dos.pdf", PDF), ana);
        assertThat(justificantes.de(List.of(baja.getId()), ana).justificantes())
                .extracting(JustificanteResponse::nombre).containsExactly("dos.pdf");
    }

    @Test
    @DisplayName("Desactivarlo no borra los que hay: se siguen viendo, pero no se suben más")
    void desactivarNoBorra() {
        activar();
        justificantes.subir(baja.getId(), fichero("parte.pdf", PDF), ana);
        justificantes.activar(false, ana);
        assertThat(justificantes.de(List.of(baja.getId()), rrhh).justificantes()).hasSize(1);
        assertThatThrownBy(() -> justificantes.subir(baja.getId(), fichero("otro.pdf", PDF), ana))
                .isInstanceOf(BusinessException.class);
    }

    private int anotados() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM accesos_a_datos WHERE que = 'JUSTIFICANTE' AND sobre_id = ?",
                Integer.class, ana.getId());
        return n == null ? 0 : n;
    }
}
