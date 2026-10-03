package com.nxtime.nxtime.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nxtime.nxtime.exception.BusinessException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Limita los intentos de entrada CONTRA UNA CUENTA, no contra una IP.
 *
 * {@link LoginRateLimitFilter} cuenta por IP, y eso frena a quien prueba
 * contraseñas desde un sitio. Pero la IP sale de una cabecera que pone quien
 * llama o el proxy de delante, así que depende de cómo esté desplegado el
 * servicio: si algún día hay un proxy más, o uno menos, la cuenta cambia. El
 * correo al que se intenta entrar, en cambio, es el mismo se mire desde donde
 * se mire.
 *
 * Por eso existen los dos límites y no uno: contra una cuenta concreta --que
 * es el ataque que de verdad importa, probar contraseñas de alguien-- este
 * frena aunque los intentos vengan de mil sitios distintos.
 *
 * Diez intentos por minuto y por cuenta. Quien de verdad entra a su cuenta no
 * hace diez intentos en un minuto ni equivocándose.
 *
 * La memoria está acotada a propósito (Caffeine con tamaño máximo y caducidad)
 * en vez de un Map que crece solo: si no, bastaría con probar correos
 * distintos para ir llenándola.
 */
@Component
public class LimitadorDeIntentosPorCuenta {

    private static final int INTENTOS_POR_MINUTO = 10;
    private static final int CUENTAS_VIGILADAS = 10_000;

    /**
     * Fallos seguidos que se admiten sin espera (revisión de seguridad del
     * 1/10/2026, ADR 034). A partir de ahí, cada fallo dobla la espera hasta
     * el siguiente intento: 1 s, 2 s, 4 s... hasta {@link #ESPERA_MAXIMA}.
     *
     * Con diez por minuto y nada más, probar contraseñas de alguien eran
     * 14.400 intentos al día. Con la espera, tras unos quince fallos queda
     * uno cada quince minutos: unos cien al día, que es el tope diario. No se
     * pone un bloqueo duro de un día porque cualquiera podría usarlo para
     * dejar a otra persona sin entrar; la espera, como mucho, la retrasa
     * quince minutos, y acertar la pone a cero.
     */
    static final int FALLOS_SIN_ESPERA = 5;
    static final Duration ESPERA_MAXIMA = Duration.ofMinutes(15);

    private final Cache<String, Bucket> intentos = Caffeine.newBuilder()
            .maximumSize(CUENTAS_VIGILADAS)
            .expireAfterAccess(Duration.ofMinutes(5))
            .build();

    /** Fallos seguidos por cuenta y cuándo fue el último. Se olvida a las 24 horas sin fallar. */
    private record Fallos(int seguidos, Instant ultimo) {
    }

    private final Cache<String, Fallos> fallos = Caffeine.newBuilder()
            .maximumSize(CUENTAS_VIGILADAS)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    private final Clock clock;

    public LimitadorDeIntentosPorCuenta() {
        this(Clock.systemUTC());
    }

    LimitadorDeIntentosPorCuenta(Clock clock) {
        this.clock = clock;
    }

    /** Un intento fallido contra esa cuenta: alarga la espera del siguiente. */
    public void fallo(String email) {
        fallos.asMap().merge(clave(email), new Fallos(1, clock.instant()),
                (antes, nuevo) -> new Fallos(antes.seguidos() + 1, nuevo.ultimo()));
    }

    /** Ha entrado: la espera vuelve a cero. */
    public void acierto(String email) {
        fallos.invalidate(clave(email));
    }

    /** La espera tras {@code seguidos} fallos: nada hasta el quinto, y luego el doble cada vez. */
    static Duration esperaTras(int seguidos) {
        if (seguidos < FALLOS_SIN_ESPERA) {
            return Duration.ZERO;
        }
        int exponente = Math.min(seguidos - FALLOS_SIN_ESPERA, 20);
        Duration espera = Duration.ofSeconds(1L << exponente);
        return espera.compareTo(ESPERA_MAXIMA) > 0 ? ESPERA_MAXIMA : espera;
    }

    private static String clave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Anota un intento contra esa cuenta y corta si ya van demasiados.
     *
     * Cuenta los intentos, no los fallos: un ataque que acierte a la décima
     * tiene que haber gastado nueve antes, y así no hace falta enterarse de
     * cómo acabó cada uno.
     */
    public void comprobar(String email) {
        String cuenta = clave(email);
        Fallos previos = fallos.getIfPresent(cuenta);
        if (previos != null) {
            Instant libre = previos.ultimo().plus(esperaTras(previos.seguidos()));
            if (clock.instant().isBefore(libre)) {
                long segundos = Math.max(1, Duration.between(clock.instant(), libre).toSeconds());
                throw new BusinessException("Demasiados intentos fallidos con esta cuenta. Espera "
                        + (segundos < 60 ? segundos + " segundos" : (segundos + 59) / 60 + " minutos")
                        + " antes de volver a probar, o recupera la contraseña.", HttpStatus.TOO_MANY_REQUESTS);
            }
        }
        Bucket bucket = intentos.get(cuenta, clave -> Bucket.builder()
                .addLimit(Bandwidth.simple(INTENTOS_POR_MINUTO, Duration.ofMinutes(1)))
                .build());

        if (!bucket.tryConsume(1)) {
            throw new BusinessException(
                    "Demasiados intentos con esta cuenta. Inténtalo de nuevo en un minuto.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
