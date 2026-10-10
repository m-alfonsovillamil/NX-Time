package com.nxtime.nxtime.security;

import com.nxtime.nxtime.exception.BusinessException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Cuántas contraseñas se comprueban a la vez: tantas como procesadores (ADR 041).
 *
 * Comprobar una contraseña es BCrypt: unos 80 ms de CPU, lento a propósito. Es
 * lo único de la aplicación que gasta procesador de verdad, y a las nueve de la
 * mañana lo pide toda la plantilla a la vez.
 *
 * <b>Por qué hace falta un tope explícito.</b> Hasta octubre de 2026 lo había
 * sin querer: el login iba en una transacción, así que nunca había más
 * contraseñas comprobándose que conexiones en el pool, cinco. El precio era
 * que esas cinco conexiones quedaban cogidas y nadie más podía ni fichar. Al
 * sacar la comprobación de la transacción el tope desapareció, y se vio
 * midiendo: con media CPU, trescientas personas entrando en un minuto pasaron
 * de tardar medio segundo a tardar cinco, porque cien hilos a la vez se
 * repartían el procesador y ninguno acababa (docs/PRUEBA-DE-CARGA.md).
 *
 * Así que el tope vuelve, pero puesto aquí y sin gastar conexiones: quien
 * espera su turno no tiene ninguna cogida, y quien ya tiene sesión y solo
 * quiere fichar no compite con cien contraseñas por la CPU.
 *
 * <b>La espera tiene tope.</b> Si el turno no llega en {@link #ESPERA_MAXIMA},
 * se responde 503 y que vuelva a intentarlo. Una cola sin límite acaba con
 * todas las peticiones agotando su espera a la vez, que es lo que pasaba.
 *
 * Como los demás límites, vive en la memoria de esta instancia.
 */
@Component
public class TurnoDeContrasena {

    private static final Logger log = LoggerFactory.getLogger(TurnoDeContrasena.class);

    /** Lo que se espera el turno antes de rendirse. */
    static final Duration ESPERA_MAXIMA = Duration.ofSeconds(20);

    private final Semaphore turnos;
    private final Duration esperaMaxima;

    /**
     * @param aLaVez cuántas a la vez; 0 (lo normal) para que sean tantas como
     *               procesadores vea la máquina. Más no acaba antes: solo las
     *               reparte peor.
     */
    @Autowired
    public TurnoDeContrasena(@Value("${application.security.contrasenas-a-la-vez:0}") int aLaVez) {
        this(aLaVez, ESPERA_MAXIMA);
    }

    TurnoDeContrasena(int aLaVez, Duration esperaMaxima) {
        int cuantas = aLaVez > 0 ? aLaVez : Math.max(1, Runtime.getRuntime().availableProcessors());
        // Justo: por orden de llegada. Sin eso, con el servicio apretado
        // alguien podría quedarse esperando mientras entran los de detrás.
        this.turnos = new Semaphore(cuantas, true);
        this.esperaMaxima = esperaMaxima;
        log.info("Contraseñas que se comprueban a la vez: {}.", cuantas);
    }

    /**
     * Hace {@code comprobacion} cuando le toca.
     *
     * @throws BusinessException 503 si el turno no llega a tiempo
     */
    public <T> T conTurno(Supplier<T> comprobacion) {
        try {
            if (!turnos.tryAcquire(esperaMaxima.toMillis(), TimeUnit.MILLISECONDS)) {
                throw ocupado();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ocupado();
        }
        try {
            return comprobacion.get();
        } finally {
            turnos.release();
        }
    }

    /** Cuántas esperan su turno ahora mismo. Para los tests. */
    int esperando() {
        return turnos.getQueueLength();
    }

    private static BusinessException ocupado() {
        return new BusinessException(
                "Hay mucha gente entrando ahora mismo. Vuelve a intentarlo en unos segundos.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
