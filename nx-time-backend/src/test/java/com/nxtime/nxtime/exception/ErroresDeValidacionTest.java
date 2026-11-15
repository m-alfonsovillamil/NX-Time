package com.nxtime.nxtime.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.nxtime.nxtime.domain.AbsenceType;
import com.nxtime.nxtime.web.TextoSinCaracterNulo;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Los 400 de una petición mal formada: en español y diciendo qué campo es
 * (revisión del 15/11/2026). Las excepciones de Jackson son las de verdad, de
 * leer un JSON con el mismo lector que registra {@code JsonDeLaApiConfig}; que
 * Spring Boot lo registre lo comprueba {@code ApiContractTest} con el backend
 * levantado.
 */
class ErroresDeValidacionTest {

    record Peticion(LocalDate fechaInicio, AbsenceType tipo, String motivo, List<Linea> lineas) {
    }

    record Linea(String nota) {
    }

    private final ErroresDeValidacion manejador = new ErroresDeValidacion();

    private final JsonMapper lector = JsonMapper.builder()
            .addModule(new SimpleModule().addDeserializer(String.class, new TextoSinCaracterNulo()))
            .build();

    private ProblemDetail leer(String json) {
        Throwable fallo = catchThrowable(() -> lector.readValue(json, Peticion.class));
        assertThat(fallo).as("el JSON tendría que fallar al leerse").isNotNull();
        return manejador.handleNotReadable(
                new HttpMessageNotReadableException("no se pudo leer", fallo, (HttpInputMessage) null));
    }

    @Test
    @DisplayName("un carácter nulo en un texto es un 400 que dice el campo (antes, un 500 de la base)")
    void caracterNulo() {
        ProblemDetail problema = leer("{\"motivo\":\"a\\u0000b\"}");

        assertThat(problema.getStatus()).isEqualTo(400);
        assertThat(problema.getDetail()).isEqualTo("El dato «motivo» lleva un carácter que no se puede guardar.");
    }

    @Test
    @DisplayName("también dentro de una lista, con su posición")
    void caracterNuloEnUnaLista() {
        ProblemDetail problema = leer("{\"lineas\":[{\"nota\":\"ok\"},{\"nota\":\"\\u0000\"}]}");

        assertThat(problema.getDetail()).isEqualTo("El dato «lineas[1].nota» lleva un carácter que no se puede guardar.");
    }

    @Test
    @DisplayName("un texto normal, con tildes y emojis, se lee igual que antes")
    void textoNormal() {
        Peticion leida = lector.readValue("{\"motivo\":\"Médico 😷 «revisión»\"}", Peticion.class);

        assertThat(leida.motivo()).isEqualTo("Médico 😷 «revisión»");
    }

    @Test
    @DisplayName("un valor fuera de su lista dice el campo, no «Failed to read request»")
    void valorQueNoExiste() {
        assertThat(leer("{\"tipo\":\"NO_EXISTE\"}").getDetail()).isEqualTo("El dato «tipo» no es válido.");
    }

    @Test
    @DisplayName("una fecha imposible dice el campo")
    void fechaImposible() {
        assertThat(leer("{\"fechaInicio\":\"2026-02-30\"}").getDetail()).isEqualTo("El dato «fechaInicio» no es válido.");
    }

    @Test
    @DisplayName("un JSON roto o vacío tiene su propio mensaje")
    void jsonRoto() {
        assertThat(leer("{no es json").getDetail()).isEqualTo("Falta el cuerpo de la petición o no es un JSON válido.");
        assertThat(manejador.handleNotReadable(new HttpMessageNotReadableException(
                "Required request body is missing", (HttpInputMessage) null)).getDetail())
                .isEqualTo("Falta el cuerpo de la petición o no es un JSON válido.");
    }

    @Test
    @DisplayName("un método que la ruta no admite: 405 en español y con su cabecera Allow")
    void metodoNoAdmitido() {
        var respuesta = manejador.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "POST")));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(respuesta.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
        assertThat(respuesta.getBody().getDetail()).isEqualTo("Esta ruta no admite PUT.");
    }

    @Test
    @DisplayName("un parámetro que falta se nombra en español")
    void parametroQueFalta() {
        ProblemDetail problema = manejador.handleMissingParameter(
                new MissingServletRequestParameterException("fecha", "LocalDate"));

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problema.getDetail()).isEqualTo("Falta el parámetro «fecha».");
    }
}
