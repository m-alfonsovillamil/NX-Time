package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.ComplaintCategory;
import com.nxtime.nxtime.domain.ComplaintStatus;
import com.nxtime.nxtime.domain.MotivoDeSupresion;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.RoleAuthorities;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ComplaintExtensionRequest;
import com.nxtime.nxtime.dto.ComplaintRedactionRequest;
import com.nxtime.nxtime.dto.ComplaintResponse;
import com.nxtime.nxtime.dto.CreateComplaintRequest;
import com.nxtime.nxtime.dto.DataAccessResponse;
import com.nxtime.nxtime.dto.UpdateComplaintChannelRequest;
import com.nxtime.nxtime.dto.UpdateComplaintStatusRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.notification.NotificationEvents;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.security.OperadoresDePlataforma;
import com.nxtime.nxtime.service.AuthService;
import com.nxtime.nxtime.service.ComplaintService;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * El canal de denuncias a la altura de la Ley 2/2023 (plan J, fase J6; ADR
 * 048), contra PostgreSQL real y como {@code nxtime_app}.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
@RecordApplicationEvents
class CanalDeDenunciasIT {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "canal_it_" + System.nanoTime();
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

    @Autowired private CanalDeDenuncias canal;
    @Autowired private ComplaintService denuncias;
    @Autowired private ConservacionDeDatos conservacion;
    @Autowired private RegistroDeAccesos accesos;
    @Autowired private AuthService authService;
    @Autowired private OperadoresDePlataforma operadores;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEvents eventos;

    private Company empresa;
    private User admin;
    private User laura;
    private User ana;

    private User persona(String nombre, Role rol) {
        return userRepository.save(User.builder().nombre(nombre).apellidos("Pruebas")
                .email(nombre.toLowerCase() + System.nanoTime() + "@canal.test").contrasena("x")
                .rol(rol).empresa(empresa).activo(true).build());
    }

    /** La persona tal como la ve el filtro de seguridad en la petición siguiente. */
    private User recargada(User u) {
        return userRepository.findById(u.getId()).orElseThrow();
    }

    private boolean instruye(User u) {
        User fresca = recargada(u);
        return operadores.principalDe(fresca).getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("denuncia:instruir"))
                && operadores.authoritiesDe(fresca).contains("denuncia:instruir");
    }

    private long ultimaDenuncia() {
        return jdbc.queryForObject("SELECT max(id) FROM denuncias WHERE empresa_id = ?", Long.class, empresa.getId());
    }

    @BeforeEach
    void empresa() {
        empresa = companyRepository.save(Company.builder().nombre("Canal " + System.nanoTime()).build());
        admin = persona("Admin", Role.ADMIN);
        laura = persona("Laura", Role.EMPLEADO);
        ana = persona("Ana", Role.EMPLEADO);
    }

    @Test
    @DisplayName("Con Responsable designado, instruye él aunque sea EMPLEADO, y los ADMIN dejan de hacerlo")
    void elResponsableInstruye() {
        assertThat(instruye(admin)).isTrue();
        assertThat(instruye(laura)).isFalse();

        canal.guardar(new UpdateComplaintChannelRequest(laura.getId(), "  Teléfono 600 000 000, de 9 a 14  "), recargada(admin));

        assertThat(instruye(laura)).isTrue();
        assertThat(instruye(admin)).isFalse();
        assertThat(canal.paraQuienDenuncia(recargada(ana)).responsable()).isEqualTo("Laura Pruebas");
        assertThat(canal.paraQuienDenuncia(recargada(ana)).viaVerbal()).isEqualTo("Teléfono 600 000 000, de 9 a 14");

        // El aviso de una denuncia nueva le llega solo a ella.
        eventos.clear();
        denuncias.presentar(new CreateComplaintRequest(ComplaintCategory.ACOSO, "Hechos", true), recargada(ana));
        assertThat(eventos.stream(NotificationEvents.ComplaintReceived.class)).singleElement()
                .satisfies(e -> assertThat(e.destinatarios()).extracting(User::getId).containsExactly(laura.getId()));

        // Si la dan de baja, vuelven a instruir los ADMIN.
        authService.setEmployeeActive(laura.getId(), false, recargada(admin));
        assertThat(recargada(admin).getEmpresa().getResponsableCanalId()).isNull();
        assertThat(instruye(admin)).isTrue();
    }

    @Test
    @DisplayName("El Responsable tiene que ser alguien activo de la empresa")
    void soloAlguienDeLaEmpresa() {
        Company otra = companyRepository.save(Company.builder().nombre("Otra " + System.nanoTime()).build());
        User ajena = userRepository.save(User.builder().nombre("Ajena").email("ajena" + System.nanoTime() + "@canal.test")
                .contrasena("x").rol(Role.ADMIN).empresa(otra).activo(true).build());

        assertThatThrownBy(() -> canal.guardar(new UpdateComplaintChannelRequest(ajena.getId(), null), recargada(admin)))
                .isInstanceOf(BusinessException.class);
        assertThat(recargada(admin).getEmpresa().getResponsableCanalId()).isNull();
    }

    @Test
    @DisplayName("Al presentarla hay acuse en el acto, con el contacto para notificaciones si se da")
    void acuseAutomatico() {
        denuncias.presentar(new CreateComplaintRequest(ComplaintCategory.FRAUDE, "Hechos", false, " Calle Real 1 "), recargada(ana));
        ComplaintResponse expediente = denuncias.detalle(ultimaDenuncia(), recargada(admin));

        assertThat(expediente.acuseReciboEn()).isEqualTo(expediente.creadoEn());
        assertThat(expediente.diasHastaAcuse()).isNull();
        assertThat(expediente.contactoNotificaciones()).isEqualTo("Calle Real 1");
        assertThat(expediente.diasHastaSupresion()).isNotNull();

        // Al pasar a investigación deja de contar la supresión.
        ComplaintResponse investigada = denuncias.cambiarEstado(ultimaDenuncia(),
                new UpdateComplaintStatusRequest(ComplaintStatus.EN_INVESTIGACION, null), recargada(admin));
        assertThat(investigada.investigacionIniciadaEn()).isNotNull();
        assertThat(investigada.diasHastaSupresion()).isNull();
    }

    @Test
    @DisplayName("El plazo se amplía a seis meses una vez, y quien denunció lo lee en el expediente")
    void prorroga() {
        denuncias.presentar(new CreateComplaintRequest(ComplaintCategory.CORRUPCION, "Hechos", true), recargada(ana));
        long id = ultimaDenuncia();
        long antes = denuncias.detalle(id, recargada(admin)).diasHastaRespuesta();

        ComplaintResponse ampliada = denuncias.prorrogar(id, new ComplaintExtensionRequest("Hay que pedir informes a tres sedes."), recargada(admin));

        LocalDate hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));
        assertThat(ampliada.diasHastaRespuesta()).isEqualTo(ChronoUnit.DAYS.between(hoy, hoy.plusMonths(6)));
        assertThat(ampliada.diasHastaRespuesta()).isGreaterThan(antes);
        assertThat(ampliada.mensajes()).singleElement()
                .satisfies(m -> assertThat(m.texto()).contains("seis meses").contains("tres sedes"));
        assertThatThrownBy(() -> denuncias.prorrogar(id, new ComplaintExtensionRequest("Otra vez"), recargada(admin)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Quien instruye puede suprimir un texto con categorías especiales; queda la marca")
    void supresionManual() {
        denuncias.presentar(new CreateComplaintRequest(ComplaintCategory.ACOSO, "Tiene una enfermedad que…", true), recargada(ana));
        long id = ultimaDenuncia();

        ComplaintResponse suprimida = denuncias.suprimirTexto(id,
                new ComplaintRedactionRequest(null, MotivoDeSupresion.CATEGORIAS_ESPECIALES), recargada(admin));

        assertThat(suprimida.descripcion()).startsWith("[Texto suprimido el ").contains("categorías especiales")
                .doesNotContain("enfermedad");
    }

    @Test
    @DisplayName("A los tres meses sin investigar se suprime el contenido; lo investigado se queda; a los diez años se va todo")
    void plazosDeConservacion() {
        User anaFresca = recargada(ana);
        denuncias.presentar(new CreateComplaintRequest(ComplaintCategory.ACOSO, "Sin investigar", false, "ana@casa.es"), anaFresca);
        long sinInvestigar = ultimaDenuncia();
        denuncias.responderComoDenunciante(sinInvestigar, new com.nxtime.nxtime.dto.ComplaintMessageRequest("Más datos"), anaFresca);
        denuncias.presentar(new CreateComplaintRequest(ComplaintCategory.FRAUDE, "Investigada", true), anaFresca);
        long investigada = ultimaDenuncia();
        denuncias.cambiarEstado(investigada, new UpdateComplaintStatusRequest(ComplaintStatus.EN_INVESTIGACION, null), recargada(admin));
        denuncias.presentar(new CreateComplaintRequest(ComplaintCategory.OTRA, "Antigua", true), anaFresca);
        long antigua = ultimaDenuncia();

        Timestamp haceCuatroMeses = Timestamp.from(Instant.now().minus(122, ChronoUnit.DAYS));
        jdbc.update("UPDATE denuncias SET creado_en = ?, acuse_recibo_en = ? WHERE id IN (?, ?)",
                haceCuatroMeses, haceCuatroMeses, sinInvestigar, investigada);
        Timestamp haceOnceAnios = Timestamp.from(Instant.now().minus(11 * 366, ChronoUnit.DAYS));
        jdbc.update("UPDATE denuncias SET creado_en = ?, acuse_recibo_en = ? WHERE id = ?", haceOnceAnios, haceOnceAnios, antigua);

        assertThat(conservacion.aplicar(Instant.now()))
                .containsEntry("denunciasSinInvestigar", 1)
                .containsEntry("libroRegistro", 1);

        var fila = jdbc.queryForMap("SELECT * FROM denuncias WHERE id = ?", sinInvestigar);
        assertThat(fila.get("descripcion")).isEqualTo(ConservacionDeDatos.DESCRIPCION_SUPRIMIDA);
        assertThat(fila.get("denunciante_id")).isNull();
        assertThat(fila.get("contacto_notificaciones")).isNull();
        assertThat(fila.get("estado")).isEqualTo("ARCHIVADA");
        assertThat(fila.get("contenido_suprimido_en")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM denuncia_mensajes WHERE denuncia_id = ?", Integer.class, sinInvestigar)).isZero();

        assertThat(jdbc.queryForObject("SELECT descripcion FROM denuncias WHERE id = ?", String.class, investigada)).isEqualTo("Investigada");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM denuncias WHERE id = ?", Integer.class, antigua)).isZero();

        // Repetir no vuelve a tocar nada.
        assertThat(conservacion.aplicar(Instant.now())).containsEntry("denunciasSinInvestigar", 0);
    }

    @Test
    @DisplayName("El libro-registro dice fechas, objeto y estado, nunca el relato; y queda anotado")
    void libroRegistro() {
        denuncias.presentar(new CreateComplaintRequest(ComplaintCategory.SEGURIDAD, "Relato secreto", false), recargada(ana));

        String csv = new String(denuncias.libroRegistro(recargada(admin)), StandardCharsets.UTF_8);

        assertThat(csv).contains("Número;Recibida;Objeto").contains("Seguridad").contains("RECIBIDA")
                .doesNotContain("Relato secreto").doesNotContain("Ana");
        assertThat(accesos.deLaEmpresa(recargada(admin), null)).extracting(DataAccessResponse::detalle)
                .contains("Libro-registro del canal de denuncias");
    }

    @Test
    @DisplayName("Con Responsable, un ADMIN no ve quién abrió cada expediente; el Responsable sí")
    void accesosReservadosAlResponsable() {
        denuncias.presentar(new CreateComplaintRequest(ComplaintCategory.ACOSO, "Hechos", true), recargada(ana));
        canal.guardar(new UpdateComplaintChannelRequest(laura.getId(), null), recargada(admin));
        denuncias.detalle(ultimaDenuncia(), recargada(laura));

        List<DataAccessResponse> delAdmin = accesos.deLaEmpresa(recargada(admin), null);
        assertThat(delAdmin).extracting(DataAccessResponse::que).doesNotContain("DENUNCIA");
        assertThat(accesos.deLosExpedientes(recargada(laura))).extracting(DataAccessResponse::que).containsExactly("DENUNCIA");
        assertThat(RoleAuthorities.tiene(recargada(admin), "denuncia:instruir")).isFalse();
    }
}
