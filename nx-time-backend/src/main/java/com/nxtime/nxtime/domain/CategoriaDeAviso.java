package com.nxtime.nxtime.domain;

/**
 * En qué se agrupan los avisos para que cada persona elija cómo le llegan
 * (ADR 059; plan N, paso N9): por correo, al móvil (push), o solo en la campana
 * de la aplicación, que siempre los recibe.
 *
 * {@link #OBLIGATORIOS} no se puede apagar: lo que tiene que ver con la cuenta y
 * su seguridad, la firma del registro, el canal de denuncias y el borrado de
 * datos. Son pocos, raros y alguien los necesita leer seguro.
 *
 * La categoría sale del NOMBRE del tipo de aviso, por familias (AUSENCIA_*,
 * CORRECCION_*...), y no de una lista: un tipo nuevo de una familia conocida
 * cae en ella sin tocar nada aquí. Uno que no encaje en ninguna va a
 * obligatorios, que es lo prudente: llega siempre.
 */
public enum CategoriaDeAviso {
    AUSENCIAS("Ausencias", "Peticiones nuevas, aprobadas, rechazadas o canceladas"),
    CORRECCIONES("Correcciones", "Correcciones de fichajes por revisar o resueltas"),
    HORAS_EXTRA("Horas extra", "Excesos detectados, la bolsa anual y el resumen del equipo"),
    INCIDENCIAS("Incidencias de cuadrante", "Retrasos, salidas anticipadas y días sin fichar"),
    OFERTAS("Ofertas internas", "Vacantes nuevas y novedades de las candidaturas"),
    EQUIPO("Equipo", "Alguien ficha en un día no laborable o un cuadrante no suma la jornada"),
    OBLIGATORIOS("Cuenta, firma y denuncias", "No se pueden apagar");

    private final String etiqueta;
    private final String descripcion;

    CategoriaDeAviso(String etiqueta, String descripcion) {
        this.etiqueta = etiqueta;
        this.descripcion = descripcion;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean configurable() {
        return this != OBLIGATORIOS;
    }

    public static CategoriaDeAviso de(NoticeType tipo) {
        String nombre = tipo.name();
        if (nombre.startsWith("AUSENCIA_")) {
            return AUSENCIAS;
        }
        if (nombre.startsWith("CORRECCION_")) {
            return CORRECCIONES;
        }
        if (nombre.contains("HORAS_EXTRA")) {
            return HORAS_EXTRA;
        }
        if (nombre.startsWith("INCIDENCIA_") || nombre.equals("RESUMEN_INCIDENCIAS")) {
            return INCIDENCIAS;
        }
        if (nombre.startsWith("OFERTA_") || nombre.startsWith("CANDIDATURA_")) {
            return OFERTAS;
        }
        if (nombre.equals("TRABAJO_EN_DIA_NO_LABORABLE") || nombre.equals("CUADRANTE_DISTINTO_DE_JORNADA")) {
            return EQUIPO;
        }
        return OBLIGATORIOS;
    }
}
