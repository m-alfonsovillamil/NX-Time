package com.nxtime.nxtime.config;

import com.nxtime.nxtime.web.TextoSinCaracterNulo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

/**
 * Lo que se añade al JSON de la API sobre lo que configura Spring Boot.
 *
 * Spring Boot registra en su {@code JsonMapper} todos los {@link JacksonModule}
 * que encuentra como beans, y ese es el que usan los controladores para leer
 * los cuerpos: no hace falta tocar nada más.
 */
@Configuration
public class JsonDeLaApiConfig {

    /** Ver {@link TextoSinCaracterNulo}. */
    @Bean
    JacksonModule textoSinCaracterNulo() {
        return new SimpleModule("texto-sin-caracter-nulo")
                .addDeserializer(String.class, new TextoSinCaracterNulo());
    }
}
