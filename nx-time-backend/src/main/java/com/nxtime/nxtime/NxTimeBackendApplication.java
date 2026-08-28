package com.nxtime.nxtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling desde la Fase 9: activa el proceso nocturno que
 * cierra las jornadas que nadie cerró (ver IncompleteTimeEntryScheduler).
 */
@SpringBootApplication
@EnableScheduling
public class NxTimeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(NxTimeBackendApplication.class, args);
    }
}
