package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.DeletionStatus;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntry;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.DeletionRequestDTO;
import com.nxtime.nxtime.dto.DeletionResponse;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.TenantAccessException;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.DeletionRequestRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.DataDeletionService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Borrado de datos personales (ADR 016) contra PostgreSQL real, conectado
 * como {@code nxtime_app}: si a la aplicación le faltara un permiso de DELETE
 * o UPDATE en alguna tabla, aquí es donde saltaría y no en producción.
 *
 * Dos cosas importan más que el resto: que lo que se borra sea exactamente lo
 * que dice el ADR (ni menos, que incumple; ni más, que destruye el registro
 * horario), y que <b>no se toque nada de otra persona</b>. Por eso cada prueba
 * siembra a Javi, de la misma empresa y con datos del mismo tipo.
 *
 * Requisito: {@code docker compose up -d postgres}.
 */
@SpringBootTest
class DataDeletionIT {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "borrado_it_" + System.nanoTime();
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
    private DataDeletionService service;
    @Autowired
    private DeletionRequestRepository deletionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Company empresa;
    private User ana;
    private User javi;
    private User rrhh;
    private User admin;

    @BeforeEach
    void setUp() {
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        ana = persona("Ana", Role.EMPLEADO);
        javi = persona("Javi", Role.EMPLEADO);
        rrhh = persona("Rita", Role.RRHH);
        admin = persona("Alba", Role.ADMIN);
    }

    private User persona(String nombre, Role rol) {
        return userRepository.save(User.builder()
                .nombre(nombre).apellidos("Pruebas").email(nombre.toLowerCase() + System.nanoTime() + "@test")
                .contrasena("$2a$10$hashoriginal").rol(rol).empresa(empresa).activo(true)
                .horasSemanales(new BigDecimal("40.0")).fechaNacimiento(LocalDate.of(1990, 5, 17))
                .puesto("Puesto de " + nombre)
                .build());
    }

    private TimeEntry fichaje(User quien, int diasAtras, boolean cerrado) {
        Instant entrada = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(diasAtras, ChronoUnit.DAYS);
        return timeEntryRepository.save(TimeEntry.builder()
                .usuario(quien).empresa(empresa)
                .horaEntrada(entrada).horaSalida(cerrado ? entrada.plus(8, ChronoUnit.HOURS) : null)
                .build());
    }

    /** Todo lo que puede tener una persona, con su nombre dentro de cada texto. */
    private TimeEntry sembrarDatos(User quien) {
        long id = quien.getId();
        String n = quien.getNombre();
        TimeEntry registro = fichaje(quien, 2, true);

        jdbc.update("INSERT INTO pausas_anadidas (empresa_id, registro_id, inicio, fin, motivo, creada_por_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                empresa.getId(), registro.getId(),
                ts(registro.getHoraEntrada().plus(4, ChronoUnit.HOURS)),
                ts(registro.getHoraEntrada().plus(5, ChronoUnit.HOURS)), "Comida de " + n, id);
        jdbc.update("INSERT INTO avisos (empresa_id, destinatario_id, tipo, titulo, cuerpo, leido, creado_en) "
                + "VALUES (?, ?, 'BIENVENIDA', ?, 'Hola', false, now())", empresa.getId(), id, "Bienvenida, " + n);
        long cv = jdbc.queryForObject("INSERT INTO adjuntos (empresa_id, usuario_id, tipo, nombre_original, mime, "
                        + "tamano_bytes, subido_en) VALUES (?, ?, 'CV', ?, 'application/pdf', 3, now()) RETURNING id",
                Long.class, empresa.getId(), id, "cv-" + n + ".pdf");
        jdbc.update("INSERT INTO adjunto_datos (adjunto_id, contenido) VALUES (?, ?)", cv, new byte[] {1, 2, 3});
        long oferta = jdbc.queryForObject("INSERT INTO ofertas_internas (empresa_id, titulo, descripcion, publicada_por) "
                + "VALUES (?, 'Oferta', 'Descripción', ?) RETURNING id", Long.class, empresa.getId(), rrhh.getId());
        jdbc.update("INSERT INTO candidaturas (oferta_id, usuario_id, adjunto_cv_id, carta) VALUES (?, ?, ?, ?)",
                oferta, id, cv, "Carta de " + n);
        jdbc.update("INSERT INTO refresh_tokens (token, usuario_id, expira_en, creado_en) "
                + "VALUES (?, ?, now() + interval '1 day', now())", "token-" + n + System.nanoTime(), id);
        jdbc.update("INSERT INTO codigos_acceso (usuario_id, tipo, codigo_hash, creado_en, expira_en) "
                + "VALUES (?, 'RECUPERACION', ?, now(), now() + interval '1 hour')", id, "hash-" + n);
        jdbc.update("INSERT INTO peticiones_ausencia (usuario_id, empresa_id, fecha_inicio, fecha_fin, tipo, estado, "
                        + "motivo, aprobado_por_id, fecha_resolucion, comentario_resolucion) "
                        + "VALUES (?, ?, current_date, current_date, 'MEDICO', 'RECHAZADA', ?, ?, now(), ?)",
                id, empresa.getId(), "Médico de " + n, rrhh.getId(), "Rechazo a " + n);
        jdbc.update("INSERT INTO solicitudes_correccion (empresa_id, registro_id, solicitante_id, hora_entrada_propuesta, "
                        + "hora_salida_propuesta, motivo, estado, aprobador_id, fecha_resolucion, comentario_resolucion) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'APROBADA', ?, now(), ?)",
                empresa.getId(), registro.getId(), id, ts(registro.getHoraEntrada()),
                ts(registro.getHoraSalida()), "Corrección de " + n, rrhh.getId(), "Visto, " + n);
        jdbc.update("INSERT INTO avisos_horas_extra (empresa_id, usuario_id, fecha, minutos_extra, minutos_esperados, "
                        + "tipo, estado, justificacion, revisado_por, fecha_revision) "
                        + "VALUES (?, ?, current_date, 60, 2400, 'SEMANAL', 'JUSTIFICADO', ?, ?, now())",
                empresa.getId(), id, "Cierre de mes de " + n, rrhh.getId());
        long denuncia = jdbc.queryForObject("INSERT INTO denuncias (empresa_id, codigo_hash, denunciante_id, categoria, "
                        + "descripcion, estado, acuse_recibo_en, resuelta_en, conclusion) "
                        + "VALUES (?, ?, ?, 'OTRA', 'Relato', 'RESUELTA', now(), now(), 'Cerrada') RETURNING id",
                Long.class, empresa.getId(), "codigo-" + n + System.nanoTime(), id);
        jdbc.update("INSERT INTO denuncia_mensajes (denuncia_id, autor_rol, autor_id, texto) "
                + "VALUES (?, 'DENUNCIANTE', ?, 'Yo'), (?, 'INSTRUCTOR', ?, 'Recibido')",
                denuncia, id, denuncia, rrhh.getId());
        return registro;
    }

    private static java.sql.Timestamp ts(Instant instante) {
        return java.sql.Timestamp.from(instante);
    }

    private int contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** Las filas de cada tabla que se purga. */
    private Map<String, Integer> prescindibleDe(User quien) {
        long id = quien.getId();
        return Map.of(
                "candidaturas", contar("SELECT COUNT(*) FROM candidaturas WHERE usuario_id = ?", id),
                "adjuntos", contar("SELECT COUNT(*) FROM adjuntos WHERE usuario_id = ?", id),
                "adjunto_datos", contar("SELECT COUNT(*) FROM adjunto_datos d JOIN adjuntos a ON a.id = d.adjunto_id "
                        + "WHERE a.usuario_id = ?", id),
                "avisos", contar("SELECT COUNT(*) FROM avisos WHERE destinatario_id = ?", id),
                "refresh_tokens", contar("SELECT COUNT(*) FROM refresh_tokens WHERE usuario_id = ?", id),
                "codigos_acceso", contar("SELECT COUNT(*) FROM codigos_acceso WHERE usuario_id = ?", id));
    }

    private DeletionResponse pedirYEjecutar(User quien) {
        long solicitud = service.solicitar(quien, new DeletionRequestDTO("Me voy de la empresa")).id();
        return service.ejecutar(solicitud, rrhh);
    }

    @Test
    @DisplayName("Ejecutar borra lo prescindible, conserva el registro horario y no toca a nadie más")
    void ejecutar_purgaLoPrescindibleYConservaElRegistro() {
        TimeEntry registro = sembrarDatos(ana);
        sembrarDatos(javi);
        Map<String, Integer> javiAntes = prescindibleDe(javi);

        DeletionResponse ejecutada = pedirYEjecutar(ana);

        assertThat(ejecutada.estado()).isEqualTo("EJECUTADA");
        assertThat(prescindibleDe(ana).values()).containsOnly(0);

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM usuarios WHERE id = ?", ana.getId());
        assertThat(fila.get("fecha_nacimiento")).isNull();
        assertThat(fila.get("activo")).isEqualTo(false);
        assertThat(fila.get("fecha_baja")).isNotNull();
        assertThat(fila.get("contrasena")).isNotEqualTo("$2a$10$hashoriginal");
        // Lo que se conserva hasta la anonimización.
        assertThat(fila.get("nombre")).isEqualTo("Ana");
        assertThat(fila.get("email")).isEqualTo(ana.getEmail());
        assertThat(contar("SELECT COUNT(*) FROM registros WHERE usuario_id = ?", ana.getId())).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM pausas_anadidas WHERE creada_por_id = ?", ana.getId())).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM peticiones_ausencia WHERE usuario_id = ?", ana.getId())).isEqualTo(1);

        // Cuatro años desde el día del último fichaje, en Madrid.
        assertThat(ejecutada.anonimizarDesde())
                .isEqualTo(registro.getHoraEntrada().atZone(MADRID).toLocalDate().plusYears(4));

        // Javi, de la misma empresa y con lo mismo, intacto.
        assertThat(prescindibleDe(javi)).isEqualTo(javiAntes);
        assertThat(userRepository.findById(javi.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    @DisplayName("Con algo abierto no se ejecuta, y no se borra nada")
    void conBloqueos_noEjecutaNiBorra() {
        sembrarDatos(ana);
        fichaje(ana, 0, false);
        jdbc.update("INSERT INTO peticiones_ausencia (usuario_id, empresa_id, fecha_inicio, fecha_fin, tipo) "
                + "VALUES (?, ?, current_date + 10, current_date + 11, 'VACACIONES')", ana.getId(), empresa.getId());
        long solicitud = service.solicitar(ana, null).id();

        assertThat(service.pendientes(rrhh)).singleElement()
                .satisfies(p -> assertThat(p.bloqueos()).containsExactly(
                        "Tiene una jornada abierta.", "Tiene 1 petición de ausencia pendiente."));

        assertThatThrownBy(() -> service.ejecutar(solicitud, rrhh))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("jornada abierta");
        assertThat(prescindibleDe(ana).values()).doesNotContain(0);
        assertThat(deletionRepository.findById(solicitud).orElseThrow().getEstado()).isEqualTo(DeletionStatus.PENDIENTE);
    }

    @Test
    @DisplayName("Nadie ejecuta su propia solicitud, ni la del único ADMIN activo")
    void propiaYUnicoAdmin() {
        long deAdmin = service.solicitar(admin, null).id();
        long deRrhh = service.solicitar(rrhh, null).id();

        assertThatThrownBy(() -> service.ejecutar(deAdmin, rrhh))
                .isInstanceOf(BusinessException.class).hasMessageContaining("único administrador");
        assertThatThrownBy(() -> service.ejecutar(deRrhh, rrhh))
                .isInstanceOf(BusinessException.class).hasMessageContaining("propia");

        // Con un segundo ADMIN activo, sí.
        persona("Otro", Role.ADMIN);
        assertThat(service.ejecutar(deAdmin, rrhh).estado()).isEqualTo("EJECUTADA");
    }

    @Test
    @DisplayName("Una solicitud de otra empresa ni se ve ni se ejecuta")
    void otraEmpresa() {
        long solicitud = service.solicitar(ana, null).id();
        Company otra = companyRepository.save(Company.builder().nombre("Otra " + System.nanoTime()).build());
        User rrhhDeOtra = userRepository.save(User.builder()
                .nombre("Fuera").email("fuera" + System.nanoTime() + "@test").contrasena("x")
                .rol(Role.RRHH).empresa(otra).activo(true).horasSemanales(new BigDecimal("40.0")).build());

        assertThat(service.pendientes(rrhhDeOtra)).isEmpty();
        assertThatThrownBy(() -> service.ejecutar(solicitud, rrhhDeOtra)).isInstanceOf(TenantAccessException.class);
        assertThatThrownBy(() -> service.rechazar(solicitud, "No", rrhhDeOtra))
                .isInstanceOf(TenantAccessException.class);
    }

    @Test
    @DisplayName("Una sola pendiente por persona; tras cancelar se puede volver a pedir")
    void unaPendienteYCancelar() {
        service.solicitar(ana, null);
        assertThatThrownBy(() -> service.solicitar(ana, null)).isInstanceOf(BusinessException.class);

        assertThat(service.cancelar(ana).estado()).isEqualTo("CANCELADA");
        assertThat(service.solicitar(ana, new DeletionRequestDTO("  ")).motivo()).isNull();
        assertThat(service.miUltimaSolicitud(ana).orElseThrow().estado()).isEqualTo("PENDIENTE");
    }

    /*
     * El caso para el que existe: alguien ya de baja, que no puede entrar a
     * pedirlo, lo pide por correo y RRHH lo registra.
     */
    @Test
    @DisplayName("RRHH registra la solicitud de alguien de baja, queda constancia, y se puede ejecutar")
    void registrar_paraAlguienDeBaja() {
        sembrarDatos(javi);
        javi.setActivo(false);
        javi = userRepository.save(javi);

        assertThat(service.candidatos(rrhh)).extracting(c -> c.id())
                .contains(javi.getId(), ana.getId()).doesNotContain(rrhh.getId());

        DeletionResponse registrada = service.registrar(rrhh, javi.getId(), "Correo del 12/09 a rrhh@empresa");

        assertThat(registrada.estado()).isEqualTo("PENDIENTE");
        assertThat(registrada.registradaPor()).isEqualTo("Rita");
        assertThat(registrada.motivo()).isEqualTo("Correo del 12/09 a rrhh@empresa");
        assertThat(service.candidatos(rrhh)).extracting(c -> c.id()).doesNotContain(javi.getId());

        assertThat(service.ejecutar(registrada.id(), rrhh).estado()).isEqualTo("EJECUTADA");
        assertThat(prescindibleDe(javi).values()).containsOnly(0);
        assertThat(service.candidatos(rrhh)).extracting(c -> c.id()).doesNotContain(javi.getId());
        assertThatThrownBy(() -> service.registrar(rrhh, javi.getId(), "Otra vez"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya se borraron");
    }

    @Test
    @DisplayName("Registrar: ni para uno mismo, ni sin decir cómo llegó, ni dos veces, ni de otra empresa")
    void registrar_reglas() {
        assertThatThrownBy(() -> service.registrar(rrhh, rrhh.getId(), "Yo mismo"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ajustes");
        assertThatThrownBy(() -> service.registrar(rrhh, ana.getId(), "  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        service.registrar(rrhh, ana.getId(), "Carta");
        assertThatThrownBy(() -> service.registrar(admin, ana.getId(), "Carta repetida"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("pendiente");

        Company otra = companyRepository.save(Company.builder().nombre("Otra " + System.nanoTime()).build());
        User rrhhDeOtra = userRepository.save(User.builder()
                .nombre("Fuera").email("fuera" + System.nanoTime() + "@test").contrasena("x")
                .rol(Role.RRHH).empresa(otra).activo(true).horasSemanales(new BigDecimal("40.0")).build());
        assertThatThrownBy(() -> service.registrar(rrhhDeOtra, javi.getId(), "Correo"))
                .isInstanceOf(TenantAccessException.class);
        assertThat(service.candidatos(rrhhDeOtra)).extracting(c -> c.id()).doesNotContain(javi.getId());
    }

    /* El CHECK de V21, por si algún día se escribe sin pasar por el servicio. */
    @Test
    @DisplayName("La base no deja registrar una solicitud en nombre de uno mismo")
    void registrar_checkDeLaBase() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO solicitudes_borrado (empresa_id, usuario_id, registrada_por_id, motivo) VALUES (?, ?, ?, 'x')",
                empresa.getId(), rrhh.getId(), rrhh.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rechazar exige comentario y no borra nada")
    void rechazar() {
        sembrarDatos(ana);
        long solicitud = service.solicitar(ana, null).id();

        assertThatThrownBy(() -> service.rechazar(solicitud, " ", rrhh))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        DeletionResponse rechazada = service.rechazar(solicitud, "Tienes un expediente abierto", rrhh);
        assertThat(rechazada.estado()).isEqualTo("RECHAZADA");
        assertThat(rechazada.comentarioResolucion()).isEqualTo("Tienes un expediente abierto");
        assertThat(prescindibleDe(ana).values()).doesNotContain(0);
    }

    @Test
    @DisplayName("La anonimización espera a su fecha, quita la identidad y los textos, y solo los de esa persona")
    void anonimizar() {
        sembrarDatos(ana);
        sembrarDatos(javi);
        DeletionResponse ejecutada = pedirYEjecutar(ana);
        LocalDate desde = ejecutada.anonimizarDesde();

        // La víspera no la toca. (Los totales que devuelve no se miran: la
        // base es compartida con las otras pruebas de la clase.)
        service.anonimizarVencidas(desde.minusDays(1));
        assertThat(jdbc.queryForObject("SELECT nombre FROM usuarios WHERE id = ?", String.class, ana.getId()))
                .isEqualTo("Ana");

        assertThat(service.anonimizarVencidas(desde)).isPositive();

        long id = ana.getId();
        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM usuarios WHERE id = ?", id);
        assertThat(fila.get("nombre")).isEqualTo("Persona eliminada");
        assertThat(fila.get("email")).isEqualTo("eliminado-" + id + "@anonimo.invalid");
        assertThat(fila.get("apellidos")).isNull();
        assertThat(fila.get("puesto")).isNull();

        // Ningún texto de Ana queda con su nombre dentro.
        String textos = jdbc.queryForObject("""
                SELECT concat_ws('|',
                  (SELECT string_agg(motivo, '|') FROM pausas_anadidas WHERE creada_por_id = ?),
                  (SELECT string_agg(concat_ws('|', motivo, comentario_resolucion), '|') FROM peticiones_ausencia WHERE usuario_id = ?),
                  (SELECT string_agg(concat_ws('|', motivo, comentario_resolucion), '|') FROM solicitudes_correccion WHERE solicitante_id = ?),
                  (SELECT string_agg(justificacion, '|') FROM avisos_horas_extra WHERE usuario_id = ?))
                """, String.class, id, id, id, id);
        assertThat(textos).doesNotContain("Ana").contains("[eliminado]");
        assertThat(contar("SELECT COUNT(*) FROM denuncias WHERE denunciante_id = ?", id)).isZero();
        assertThat(contar("SELECT COUNT(*) FROM denuncia_mensajes WHERE autor_id = ?", id)).isZero();
        // El mensaje del instructor conserva su autor: el CHECK lo exige.
        assertThat(contar("SELECT COUNT(*) FROM denuncia_mensajes WHERE autor_id = ?", rrhh.getId())).isEqualTo(2);
        // El registro horario sigue ahí.
        assertThat(contar("SELECT COUNT(*) FROM registros WHERE usuario_id = ?", id)).isEqualTo(1);

        assertThat(deletionRepository.findById(ejecutada.id()).orElseThrow())
                .satisfies(s -> {
                    assertThat(s.getMotivo()).isNull();
                    assertThat(s.getAnonimizadaEn()).isNotNull();
                });

        // Javi, intacto.
        assertThat(jdbc.queryForObject("SELECT string_agg(motivo, '|') FROM pausas_anadidas WHERE creada_por_id = ?",
                String.class, javi.getId())).isEqualTo("Comida de Javi");
        assertThat(contar("SELECT COUNT(*) FROM denuncias WHERE denunciante_id = ?", javi.getId())).isEqualTo(1);

        // Y una segunda pasada no vuelve a tocarla. (No se mira el total que
        // devuelve: la base es compartida con las otras pruebas de la clase,
        // que dejan sus propias ejecutadas.)
        Instant primera = deletionRepository.findById(ejecutada.id()).orElseThrow().getAnonimizadaEn();
        service.anonimizarVencidas(desde.plusYears(1));
        assertThat(deletionRepository.findById(ejecutada.id()).orElseThrow().getAnonimizadaEn()).isEqualTo(primera);
    }
}
