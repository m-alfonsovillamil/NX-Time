package com.nxtime.nxtime.domain;

/**
 * Por qué quien instruye suprime un texto de un expediente de denuncia
 * (Ley 2/2023, arts. 30 y 32; ADR 048). Lo suprimido no se puede recuperar:
 * queda la marca de que se suprimió, cuándo y por qué.
 */
public enum MotivoDeSupresion {
    CATEGORIAS_ESPECIALES("contenía datos de categorías especiales (salud, ideología, vida sexual…)"),
    NO_PERTINENTE("no era pertinente para la investigación"),
    NO_VERAZ("se acreditó que no era veraz");

    private final String texto;

    MotivoDeSupresion(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}
