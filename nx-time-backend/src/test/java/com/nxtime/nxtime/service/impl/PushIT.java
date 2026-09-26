package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.NoticeType;
import com.nxtime.nxtime.domain.PushPlatform;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CreateNoticeCommand;
import com.nxtime.nxtime.notification.PushGateway;
import com.nxtime.nxtime.notification.TextoDePush;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.PushDeviceRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.NoticeService;
import com.nxtime.nxtime.service.PushDeviceService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El push de punta a punta, contra PostgreSQL real (Fase B5).
 *
 * Lo que un test con mocks no puede ver: que el push sale <b>después del
 * commit</b> del aviso y por <b>otro hilo</b> (el aviso se guarda en una
 * transacción REQUIRES_NEW, y el listener cuelga de su AFTER_COMMIT), que
 * lleva el texto genérico y no el del aviso, que un token muerto desaparece
 * de la tabla, y que el registro y el borrado de datos hacen lo que dicen.
 *
 * Google no interviene: la pasarela es una de mentira que apunta lo que se le
 * manda y dice qué tokens están muertos.
 */
@SpringBootTest
@Import(PushIT.PasarelaDePrueba.class)
@DisplayName("Notificaciones push")
class PushIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "push_it_" + System.nanoTime();
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

    /** Apunta cada envío; los tokens de {@link #muertos} vuelven como "ya no valen". */
    @TestConfiguration
    static class PasarelaDePrueba {

        static final List<Envio> ENVIOS = new CopyOnWriteArrayList<>();
        static final Set<String> MUERTOS = java.util.concurrent.ConcurrentHashMap.newKeySet();

        record Envio(List<String> tokens, Map<String, String> datos, String hilo) {
        }

        @Bean
        PushGateway pushGateway() {
            return (tokens, datos) -> {
                ENVIOS.add(new Envio(List.copyOf(tokens), Map.copyOf(datos), Thread.currentThread().getName()));
                return tokens.stream().filter(MUERTOS::contains).toList();
            };
        }
    }

    @Autowired private NoticeService noticeService;
    @Autowired private PushDeviceService pushDeviceService;
    @Autowired private PushDeviceRepository deviceRepository;
    @Autowired private PersonalDataEraser eraser;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;

    private Company empresa;
    private User ana;
    private User luis;

    @BeforeEach
    void setUp() {
        PasarelaDePrueba.ENVIOS.clear();
        PasarelaDePrueba.MUERTOS.clear();
        empresa = companyRepository.save(Company.builder().nombre("Empresa " + System.nanoTime()).build());
        ana = persona("Ana");
        luis = persona("Luis");
    }

    @Test
    @DisplayName("Un aviso sale por push después del commit, en otro hilo, y con el texto genérico")
    void unAvisoSalePorPush() throws Exception {
        pushDeviceService.registrar(ana, "token-ana", PushPlatform.ANDROID);

        noticeService.publicar(new CreateNoticeCommand(empresa.getId(), ana.getId(), NoticeType.AUSENCIA_RESUELTA,
                "Tu ausencia ha sido rechazada", "Coincide con el cierre de la oficina", "ausencias"));

        PasarelaDePrueba.Envio envio = esperarEnvio();
        assertThat(envio.tokens()).containsExactly("token-ana");
        assertThat(envio.datos())
                .containsEntry("ruta", "ausencias")
                .containsEntry("tipo", "AUSENCIA_RESUELTA")
                .containsEntry("cuerpo", TextoDePush.de(NoticeType.AUSENCIA_RESUELTA));
        // Lo que dice el aviso no viaja por Google.
        assertThat(envio.datos().values()).noneMatch(v -> v.contains("rechazada") || v.contains("cierre"));
        assertThat(envio.hilo()).isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    @DisplayName("Un token que FCM da por muerto se borra de la tabla")
    void tokenMuertoSeBorra() throws Exception {
        pushDeviceService.registrar(ana, "token-vivo", PushPlatform.ANDROID);
        pushDeviceService.registrar(ana, "token-muerto", PushPlatform.ANDROID);
        PasarelaDePrueba.MUERTOS.add("token-muerto");

        noticeService.publicar(aviso(ana));
        esperarEnvio();

        assertThat(esperar(() -> deviceRepository.findByToken("token-muerto").isEmpty())).isTrue();
        assertThat(deviceRepository.findByToken("token-vivo")).isPresent();
    }

    @Test
    @DisplayName("En un móvil compartido, el token pasa a quien entra: los avisos de la anterior dejan de llegarle")
    void elTokenCambiaDeDueno() throws Exception {
        pushDeviceService.registrar(ana, "token-compartido", PushPlatform.ANDROID);
        pushDeviceService.registrar(luis, "token-compartido", PushPlatform.ANDROID);

        assertThat(deviceRepository.findTokensDeUsuarioActivo(ana.getId())).isEmpty();
        assertThat(deviceRepository.findTokensDeUsuarioActivo(luis.getId())).containsExactly("token-compartido");
        assertThat(deviceRepository.count()).isGreaterThanOrEqualTo(1);

        noticeService.publicar(aviso(ana));
        Thread.sleep(500);
        assertThat(PasarelaDePrueba.ENVIOS).isEmpty();
    }

    @Test
    @DisplayName("Solo se da de baja un dispositivo propio")
    void bajaSoloDeLoPropio() {
        pushDeviceService.registrar(ana, "token-de-ana", PushPlatform.ANDROID);

        pushDeviceService.darDeBaja(luis, "token-de-ana");
        assertThat(deviceRepository.findByToken("token-de-ana")).isPresent();

        pushDeviceService.darDeBaja(ana, "token-de-ana");
        assertThat(deviceRepository.findByToken("token-de-ana")).isEmpty();
    }

    @Test
    @DisplayName("A quien está dado de baja no se le manda nada, aunque su móvil siga registrado")
    void aUnaBajaNoSeLeManda() throws Exception {
        pushDeviceService.registrar(ana, "token-de-baja", PushPlatform.ANDROID);
        ana.setActivo(false);
        userRepository.save(ana);

        noticeService.publicar(aviso(ana));
        Thread.sleep(500);

        assertThat(PasarelaDePrueba.ENVIOS).isEmpty();
    }

    @Test
    @DisplayName("El borrado de datos personales se lleva los dispositivos")
    void elBorradoSeLlevaLosDispositivos() {
        pushDeviceService.registrar(ana, "token-a-borrar", PushPlatform.ANDROID);

        Map<String, Integer> borrado = eraser.purgar(ana.getId());

        assertThat(borrado).containsEntry("dispositivosPush", 1);
        assertThat(deviceRepository.findByToken("token-a-borrar")).isEmpty();
    }

    // ------------------------------------------------------------------

    private CreateNoticeCommand aviso(User destinatario) {
        return new CreateNoticeCommand(empresa.getId(), destinatario.getId(), NoticeType.INCIDENCIA_DETECTADA,
                "Incidencia", "Llegaste tarde", "incidencias");
    }

    private User persona(String nombre) {
        return userRepository.save(User.builder()
                .nombre(nombre).email(nombre.toLowerCase() + System.nanoTime() + "@test").contrasena("x")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build());
    }

    /** El push va por un hilo aparte: se espera a que llegue, con tope. */
    private PasarelaDePrueba.Envio esperarEnvio() throws InterruptedException {
        assertThat(esperar(() -> !PasarelaDePrueba.ENVIOS.isEmpty())).as("el push no llegó a salir").isTrue();
        return new ArrayList<>(PasarelaDePrueba.ENVIOS).get(0);
    }

    private static boolean esperar(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (condicion.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }
}
