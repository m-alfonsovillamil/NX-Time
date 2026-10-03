package com.nxtime.nxtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Breadcrumb;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimpiezaDeSentryTest {

    @Test
    @DisplayName("Una miga de pan con un correo sale con el correo tapado")
    void migaSinCorreo() {
        Breadcrumb miga = new Breadcrumb("Login correcto: Ana.Perez@empresa.es desde la web");

        assertThat(LimpiezaDeSentry.limpiar(miga).getMessage()).isEqualTo("Login correcto: [correo] desde la web");
    }

    @Test
    @DisplayName("Del evento se tapan el mensaje, sus parámetros y el texto de las excepciones")
    void eventoSinCorreos() {
        Message mensaje = new Message();
        mensaje.setMessage("No se pudo enviar a {}");
        mensaje.setFormatted("No se pudo enviar a ana@empresa.es");
        mensaje.setParams(List.of("ana@empresa.es"));
        SentryException excepcion = new SentryException();
        excepcion.setValue("duplicate key (email)=(luis@empresa.es) already exists");
        SentryEvent evento = new SentryEvent();
        evento.setMessage(mensaje);
        evento.setExceptions(List.of(excepcion));

        SentryEvent limpio = LimpiezaDeSentry.limpiar(evento);

        assertThat(limpio.getMessage().getFormatted()).isEqualTo("No se pudo enviar a [correo]");
        assertThat(limpio.getMessage().getParams()).containsExactly("[correo]");
        assertThat(limpio.getExceptions().get(0).getValue()).isEqualTo("duplicate key (email)=([correo]) already exists");
    }

    @Test
    @DisplayName("Lo que no es un correo se queda como está")
    void loDemasIgual() {
        assertThat(LimpiezaDeSentry.sinCorreos("Fichaje 42 del usuario 7 a las 9:00")).isEqualTo("Fichaje 42 del usuario 7 a las 9:00");
        assertThat(LimpiezaDeSentry.sinCorreos(null)).isNull();
    }
}
