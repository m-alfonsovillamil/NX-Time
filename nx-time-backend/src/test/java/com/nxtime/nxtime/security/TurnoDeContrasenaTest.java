package com.nxtime.nxtime.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nxtime.nxtime.exception.BusinessException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Las contraseñas se comprueban por turnos (ADR 041).
 *
 * El tope existía sin querer --el login iba en una transacción y no había más
 * comprobaciones que conexiones-- y al quitar la transacción se perdió: con
 * media CPU, trescientas entradas en un minuto pasaron de medio segundo a
 * cinco. Estos tests son los que habrían avisado.
 */
class TurnoDeContrasenaTest {

    @Test
    @DisplayName("Nunca hay más comprobaciones a la vez que turnos, por muchas que lleguen juntas")
    void nuncaMasALaVezQueTurnos() throws Exception {
        TurnoDeContrasena turno = new TurnoDeContrasena(2, Duration.ofSeconds(20));
        AtomicInteger ahora = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();

        ExecutorService hilos = Executors.newFixedThreadPool(12);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<Integer>> comprobaciones = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                int quien = i;
                comprobaciones.add(hilos.submit(() -> {
                    salida.await();
                    return turno.conTurno(() -> {
                        maximo.accumulateAndGet(ahora.incrementAndGet(), Math::max);
                        dormir(30);
                        ahora.decrementAndGet();
                        return quien;
                    });
                }));
            }
            salida.countDown();
            for (int i = 0; i < 12; i++) {
                assertThat(comprobaciones.get(i).get(20, TimeUnit.SECONDS)).isEqualTo(i);
            }
        } finally {
            hilos.shutdownNow();
        }

        assertThat(maximo.get()).isEqualTo(2);
        assertThat(turno.esperando()).isZero();
    }

    @Test
    @DisplayName("Si el turno no llega a tiempo, 503: no se queda esperando para siempre")
    void siNoLlegaElTurno_503() throws Exception {
        TurnoDeContrasena turno = new TurnoDeContrasena(1, Duration.ofMillis(150));
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);

        ExecutorService hilos = Executors.newSingleThreadExecutor();
        try {
            Future<String> laQueTieneElTurno = hilos.submit(() -> turno.conTurno(() -> {
                dentro.countDown();
                esperar(soltar);
                return "hecha";
            }));
            assertThat(dentro.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> turno.conTurno(() -> "no debería llegar"))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

            soltar.countDown();
            assertThat(laQueTieneElTurno.get(5, TimeUnit.SECONDS)).isEqualTo("hecha");
        } finally {
            hilos.shutdownNow();
        }
        // Y con el turno libre otra vez, la siguiente pasa.
        assertThat(turno.conTurno(() -> "ahora sí")).isEqualTo("ahora sí");
    }

    @Test
    @DisplayName("Una contraseña mal no se queda con el turno: lo devuelve aunque la comprobación lance")
    void siLaComprobacionLanza_elTurnoVuelve() {
        TurnoDeContrasena turno = new TurnoDeContrasena(1, Duration.ofMillis(150));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> turno.conTurno(() -> {
                throw new IllegalStateException("credenciales incorrectas");
            })).isInstanceOf(IllegalStateException.class);
        }

        assertThat(turno.conTurno(() -> "pasa")).isEqualTo("pasa");
    }

    @Test
    @DisplayName("Sin decir cuántas, tantas como procesadores, y como poco una")
    void porDefecto_tantasComoProcesadores() throws Exception {
        TurnoDeContrasena turno = new TurnoDeContrasena(0);

        assertThat(turno.conTurno(() -> "pasa")).isEqualTo("pasa");
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void esperar(CountDownLatch cerrojo) {
        try {
            cerrojo.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
