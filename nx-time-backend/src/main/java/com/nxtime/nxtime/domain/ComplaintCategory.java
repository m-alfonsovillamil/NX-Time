package com.nxtime.nxtime.domain;

/**
 * De qué va la denuncia (Fase G).
 *
 * La lista es corta a propósito. Un desplegable de treinta categorías
 * hace que quien denuncia se pare a elegir bien, y la elección no
 * cambia nada de lo que pasa después: la instruye la misma persona y
 * con los mismos plazos. Sirve para ordenar la bandeja, no para
 * clasificar el expediente.
 *
 * {@link #OTRA} existe por lo mismo: obligar a encajar un hecho en una
 * casilla que no le corresponde es una forma de que no se denuncie.
 */
public enum ComplaintCategory {

    ACOSO("Acoso laboral o sexual"),
    DISCRIMINACION("Discriminación"),
    FRAUDE("Fraude o irregularidad contable"),
    SEGURIDAD("Seguridad y salud en el trabajo"),
    CORRUPCION("Corrupción o soborno"),
    PROTECCION_DATOS("Protección de datos"),
    OTRA("Otra");

    private final String etiqueta;

    ComplaintCategory(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
