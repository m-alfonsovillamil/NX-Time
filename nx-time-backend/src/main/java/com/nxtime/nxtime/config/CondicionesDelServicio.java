package com.nxtime.nxtime.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Quién presta el servicio, qué versión de sus condiciones está en vigor y si
 * las empresas nuevas esperan aprobación (plan J, ADR 046). Todo sale de
 * {@code application.titular}, {@code application.legal} y
 * {@code application.alta}: ver application.yml.
 */
@Component
public class CondicionesDelServicio {

    private static final Logger log = LoggerFactory.getLogger(CondicionesDelServicio.class);

    private final String producto;
    private final String titular;
    private final String correoPrivacidad;
    private final String web;
    private final String version;
    private final boolean requiereAprobacion;

    public CondicionesDelServicio(
            @Value("${application.titular.producto}") String producto,
            @Value("${application.titular.nombre}") String titular,
            @Value("${application.titular.correo-privacidad}") String correoPrivacidad,
            @Value("${application.titular.web}") String web,
            @Value("${application.legal.version}") String version,
            @Value("${application.alta.requiere-aprobacion:true}") boolean requiereAprobacion) {
        this.producto = producto;
        this.titular = titular;
        this.correoPrivacidad = correoPrivacidad;
        this.web = web.endsWith("/") ? web.substring(0, web.length() - 1) : web;
        this.version = version;
        this.requiereAprobacion = requiereAprobacion;
        log.info("Condiciones del servicio en vigor: versión {}. Las empresas nuevas {}.",
                version, requiereAprobacion ? "esperan aprobación" : "entran sin aprobación");
    }

    public String producto() {
        return producto;
    }

    public String titular() {
        return titular;
    }

    /** El correo al que escribir por privacidad, y por cualquier cosa del servicio. */
    public String correoPrivacidad() {
        return correoPrivacidad;
    }

    /** La web, sin barra al final. */
    public String web() {
        return web;
    }

    public String version() {
        return version;
    }

    public boolean requiereAprobacion() {
        return requiereAprobacion;
    }
}
