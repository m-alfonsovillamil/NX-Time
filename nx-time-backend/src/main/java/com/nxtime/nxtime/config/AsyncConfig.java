package com.nxtime.nxtime.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Ejecución asíncrona (Fase 10), usada por el envío de correos.
 *
 * Se define un executor propio en vez de dejar el que Spring Boot pone
 * por defecto para poder acotar la cola: con la cola sin límite, una
 * caída prolongada del servidor SMTP iría acumulando correos en memoria
 * hasta tumbar la aplicación. Con {@code CallerRunsPolicy}, si la cola
 * se llena el correo se envía en el hilo que lo pidió: se ralentiza esa
 * petición, que es mucho menos grave que quedarse sin memoria o
 * perder la notificación en silencio.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String EMAIL_EXECUTOR = "emailExecutor";

    @Bean(EMAIL_EXECUTOR)
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
