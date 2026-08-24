package com.nxtime.nxtime

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
class NxTimeBackendApplication

/*
 * Esta es la función principal que arranca toda la aplicación de backend.
 */
fun main(args: Array<String>) {

    runApplication<NxTimeBackendApplication>(*args)
}
