package com.nxtime.nxtime.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caché en memoria con Caffeine (Fase 10).
 *
 * Dos usos, con vidas distintas a propósito porque los datos cambian a
 * ritmos muy distintos:
 *
 *  - {@link #FESTIVOS}: el calendario laboral de una empresa y un año.
 *    Cambia como mucho una vez al año, y se consultaba una vez por cada
 *    ausencia al montar un listado (el N+1 sobre "festivos" que quedó
 *    anotado en la Fase 9). Vida larga.
 *
 *  - {@link #DASHBOARD}: los agregados del panel. Son consultas
 *    GROUP BY sobre todo el histórico de fichajes, y nadie necesita que
 *    el contador de horas del mes esté al segundo. Vida corta: un minuto
 *    basta para absorber los refrescos de pantalla sin que el dato se
 *    quede visiblemente viejo.
 *
 * Es caché de proceso, no distribuida: con varias instancias cada una
 * tendría la suya (misma limitación consciente que LoginRateLimitFilter).
 * Para el alcance de este proyecto -- una sola instancia -- sobra, y
 * evita meter Redis solo para esto.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String FESTIVOS = "festivos";
    public static final String DASHBOARD = "dashboard";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache(FESTIVOS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(6))
                .maximumSize(500)
                .build());
        manager.registerCustomCache(DASHBOARD, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(1_000)
                .build());
        return manager;
    }
}
