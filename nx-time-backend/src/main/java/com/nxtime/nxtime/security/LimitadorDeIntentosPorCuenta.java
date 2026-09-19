package com.nxtime.nxtime.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nxtime.nxtime.exception.BusinessException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
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

    private final Cache<String, Bucket> intentos = Caffeine.newBuilder()
            .maximumSize(CUENTAS_VIGILADAS)
            .expireAfterAccess(Duration.ofMinutes(5))
            .build();

    /**
     * Anota un intento contra esa cuenta y corta si ya van demasiados.
     *
     * Cuenta los intentos, no los fallos: un ataque que acierte a la décima
     * tiene que haber gastado nueve antes, y así no hace falta enterarse de
     * cómo acabó cada uno.
     */
    public void comprobar(String email) {
        String cuenta = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
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
