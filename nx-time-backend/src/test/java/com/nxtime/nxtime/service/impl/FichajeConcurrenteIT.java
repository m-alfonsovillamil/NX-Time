package com.nxtime.nxtime.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nxtime.nxtime.domain.Company;
import com.nxtime.nxtime.domain.Role;
import com.nxtime.nxtime.domain.TimeEntryAction;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.TimeEntryRequest;
import com.nxtime.nxtime.exception.BusinessException;
import com.nxtime.nxtime.exception.GlobalExceptionHandler;
import com.nxtime.nxtime.repository.CompanyRepository;
import com.nxtime.nxtime.repository.TimeEntryRepository;
import com.nxtime.nxtime.repository.UserRepository;
import com.nxtime.nxtime.service.TimeEntryService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Dos fichajes de entrada a la vez, de la misma persona (Fase A2).
 *
 * <h2>Qué se está probando</h2>
 *
 * {@code TimeEntryServiceImpl} comprueba antes de insertar si ya hay una
 * jornada abierta, y lanza {@code BusinessException("Ya hay una jornada
 * activa.")}. Esa comprobación da el mensaje bueno, pero no es una garantía:
 * entre el SELECT y el INSERT cabe otra petición. La garantía la da el índice
 * parcial {@code uq_registros_jornada_abierta} de V1.
 *
 * El problema era lo que pasaba cuando ganaba el índice: {@code
 * GlobalExceptionHandler} no tenía manejador de {@code
 * DataIntegrityViolationException}, así que caía en el 500 genérico. Es decir,
 * a quien fichara dos veces seguidas con el dedo nervioso --o con la red
 * lenta-- el sistema le contestaba "Ha ocurrido un error inesperado" en vez de
 * "ya hay una jornada activa", y lo apuntaba en Sentry como fallo del
 * servidor.
 *
 * <h2>Qué NO cubre</h2>
 *
 * Esto llega hasta la excepción y la pasa por el manejador real, pero no monta
 * una petición HTTP: no comprueba la serialización del ProblemDetail ni las
 * cabeceras. Eso lo cubren los tests de slice de los controladores; lo que
 * aquí hace falta es una carrera de verdad contra PostgreSQL de verdad, que en
 * un slice no se puede provocar.
 */
@SpringBootTest
class FichajeConcurrenteIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) throws Exception {
        String testDb = "fichaje_concurrente_it_" + System.nanoTime();
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

    private static final Logger log = LoggerFactory.getLogger(FichajeConcurrenteIT.class);

    @Autowired
    private TimeEntryService timeEntryService;
    @Autowired
    private TimeEntryRepository timeEntryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private String darDeAltaEmpleado() {
        Company empresa = companyRepository.save(
                Company.builder().nombre("Empresa doble fichaje " + System.nanoTime()).build());
        String email = "doble" + System.nanoTime() + "@nxtime.test";
        userRepository.save(User.builder()
                .email(email).nombre("Empleado").contrasena("hash")
                .rol(Role.EMPLEADO).empresa(empresa).activo(true).build());
        return email;
    }

    @Test
    @DisplayName("Dos entradas simultáneas: una entra, la otra es un 409 -- nunca un 500")
    void dosEntradasALaVez_unaEntraYLaOtraEsConflicto() throws Exception {
        String email = darDeAltaEmpleado();

        AtomicInteger exitos = new AtomicInteger();
        List<Throwable> fallos = new CopyOnWriteArrayList<>();
        CountDownLatch enPosicion = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(2);

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                hilos.submit(() -> {
                    try {
                        enPosicion.countDown();
                        salida.await();
                        timeEntryService.registerTimeEntry(email, new TimeEntryRequest(TimeEntryAction.INICIO));
                        exitos.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Throwable e) {
                        fallos.add(e);
                    } finally {
                        terminados.countDown();
                    }
                });
            }
            assertThat(enPosicion.await(30, TimeUnit.SECONDS)).isTrue();
            salida.countDown();
            assertThat(terminados.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            hilos.shutdownNow();
        }

        // Exactamente una jornada abierta: eso es lo que protege el dato, y ya
        // funcionaba antes de esta fase.
        assertThat(exitos.get()).as("solo una de las dos entradas puede prosperar").isEqualTo(1);
        assertThat(timeEntryRepository.findAll()).hasSize(1);

        // Lo que cambia en A2 es qué se le contesta a la que pierde.
        assertThat(fallos).as("la segunda entrada tiene que fallar").hasSize(1);
        Throwable perdedora = fallos.get(0);

        // Cuál de las dos barreras saltó no se fija a propósito (ver abajo),
        // pero interesa verlo en la salida del test: si dejara de salir nunca
        // DataIntegrityViolationException, esta carrera habría dejado de
        // ejercitar el camino que A2 arregla y habría que reforzarla.
        log.info("La entrada perdedora falló con {}", perdedora.getClass().getSimpleName());

        int estado = estadoQueDevolveriaLaApi(perdedora);
        assertThat(estado)
                .as("fichar dos veces a la vez es un conflicto, no un fallo del servidor (fue: %s)", perdedora)
                .isEqualTo(HttpStatus.CONFLICT.value());
    }

    /**
     * Qué código habría contestado la API ante esa excepción.
     *
     * Hay dos desenlaces legítimos y los dos tienen que acabar en 409, porque
     * cuál ocurre depende del momento exacto en que se crucen las dos
     * transacciones:
     *
     * <ul>
     *   <li>gana la comprobación previa del servicio -> {@code BusinessException},
     *       que ya daba 409 desde siempre;</li>
     *   <li>gana el índice de la base -> {@code DataIntegrityViolationException},
     *       que hasta esta fase daba 500.</li>
     * </ul>
     *
     * Atar el test a uno de los dos lo haría intermitente sin motivo.
     */
    private int estadoQueDevolveriaLaApi(Throwable excepcion) {
        if (excepcion instanceof BusinessException negocio) {
            return handler.handleBusiness(negocio).getStatus();
        }
        if (excepcion instanceof DataIntegrityViolationException integridad) {
            return handler.handleDataIntegrity(integridad).getStatus();
        }
        throw new AssertionError("Fallo inesperado al fichar dos veces a la vez", excepcion);
    }
}
