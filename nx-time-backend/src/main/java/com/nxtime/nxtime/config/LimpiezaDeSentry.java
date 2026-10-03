package com.nxtime.nxtime.config;

import io.sentry.Breadcrumb;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lo que sale hacia Sentry, sin correos electrónicos (revisión de seguridad
 * del 1/10/2026, ADR 034).
 *
 * <p>Los logs ya escriben el id y no el correo de quien hace algo, pero un
 * correo puede colarse igual: en el mensaje de una excepción de la base («la
 * clave (email)=(ana@...) ya existe»), en el de un fallo del servidor de
 * correo, o en un log que se escriba mañana. Sentry es un tercero, fuera de
 * la UE, y {@code send-default-pii: false} solo quita la IP y las cabeceras:
 * los mensajes viajan tal cual. Aquí se tapan los correos de las migas de pan
 * (los INFO y WARN que acompañan a un error), del mensaje del evento y de las
 * excepciones, antes de que salgan.
 *
 * <p>El starter de Sentry recoge solos los beans de estos dos tipos.
 */
@Configuration
public class LimpiezaDeSentry {

    static final Pattern CORREO = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    static final String TAPADO = "[correo]";

    static String sinCorreos(String texto) {
        return texto == null ? null : CORREO.matcher(texto).replaceAll(TAPADO);
    }

    @Bean
    public SentryOptions.BeforeBreadcrumbCallback migasSinCorreos() {
        return (miga, pista) -> limpiar(miga);
    }

    @Bean
    public SentryOptions.BeforeSendCallback eventosSinCorreos() {
        return (evento, pista) -> limpiar(evento);
    }

    static Breadcrumb limpiar(Breadcrumb miga) {
        miga.setMessage(sinCorreos(miga.getMessage()));
        return miga;
    }

    static SentryEvent limpiar(SentryEvent evento) {
        Message mensaje = evento.getMessage();
        if (mensaje != null) {
            mensaje.setMessage(sinCorreos(mensaje.getMessage()));
            mensaje.setFormatted(sinCorreos(mensaje.getFormatted()));
            List<String> parametros = mensaje.getParams();
            if (parametros != null) {
                mensaje.setParams(parametros.stream().map(LimpiezaDeSentry::sinCorreos).toList());
            }
        }
        List<SentryException> excepciones = evento.getExceptions();
        if (excepciones != null) {
            excepciones.forEach(excepcion -> excepcion.setValue(sinCorreos(excepcion.getValue())));
        }
        return evento;
    }
}
