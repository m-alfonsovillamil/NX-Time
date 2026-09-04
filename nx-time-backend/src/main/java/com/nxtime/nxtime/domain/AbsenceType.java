package com.nxtime.nxtime.domain;

/**
 * Tipos de ausencia que se pueden solicitar.
 *
 * Constantes en español a propósito (ver Role.java): son el valor real
 * del campo "tipo" en el JSON y en el CHECK constraint de la BD.
 *
 * {@link #getEtiqueta()} es ese mismo valor escrito para leerse. El
 * nombre de la constante es el contrato ({@code FALLECIMIENTO_FAMILIAR});
 * la etiqueta es lo que ve una persona, y hasta ahora el servidor no
 * tenía ninguna: los correos escribían "Tu petición de VACACIONES", en
 * mayúsculas y con guiones bajos. La app Android sí traduce cada tipo
 * (ver {@code TipoAusencia.etiqueta} en {@code ui/util}), así que el
 * correo era el único sitio donde se colaba el enum crudo -- y el aviso
 * in-app de la Fase A habría sido el segundo.
 */
public enum AbsenceType {
    VACACIONES("Vacaciones"),
    ASUNTOS_PROPIOS("Asuntos propios"),
    MATRIMONIO("Matrimonio"),
    FALLECIMIENTO_FAMILIAR("Fallecimiento de un familiar"),
    HOSPITALIZACION_FAMILIAR("Hospitalización de un familiar"),
    LACTANCIA("Lactancia"),
    MATERNIDAD_PATERNIDAD("Maternidad o paternidad"),
    MEDICO("Consulta médica"),
    TRASLADO_DOMICILIO("Traslado de domicilio"),
    VIAJE_TRABAJO("Viaje de trabajo"),
    OTROS("Otros");

    private final String etiqueta;

    AbsenceType(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
