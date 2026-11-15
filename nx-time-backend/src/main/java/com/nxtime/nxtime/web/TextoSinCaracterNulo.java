package com.nxtime.nxtime.web;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Lee los textos del JSON como siempre, pero no deja pasar el carácter nulo
 * (U+0000).
 *
 * PostgreSQL no puede guardarlo en una columna de texto (SQLSTATE 22021), y
 * hasta noviembre de 2026 un {@code "\u0000"} en cualquier campo llegaba hasta
 * el INSERT y salía como un 500, «error inesperado»: 28 rutas distintas en la
 * revisión del 15/11. Ninguna persona lo escribe a mano, pero sí lo puede
 * traer un texto pegado de otro sitio, y en todo caso no es un fallo del
 * servidor sino un dato que no vale.
 *
 * Se rechaza aquí, al leer, y no al guardar: así no llega a ningún sitio
 * (ni a un correo, ni al log, ni a la huella de la auditoría) y el 400 dice qué
 * campo es. {@code ErroresDeValidacion} reconoce la excepción por su valor; el
 * manejador de la base sigue teniendo una red para lo que llegue por otro
 * camino (un parámetro de la URL, una cabecera).
 */
public final class TextoSinCaracterNulo extends StringDeserializer {

    public static final char NULO = '\u0000';

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) {
        String texto = super.deserialize(p, ctxt);
        if (texto != null && texto.indexOf(NULO) >= 0) {
            throw InvalidFormatException.from(p,
                    "El texto lleva un carácter nulo (U+0000), que no se puede guardar.", texto, String.class);
        }
        return texto;
    }

    /** Si es la excepción que lanza este lector, para darle su mensaje. */
    public static boolean esSuya(InvalidFormatException ex) {
        return ex.getTargetType() == String.class
                && ex.getValue() instanceof String valor
                && valor.indexOf(NULO) >= 0;
    }
}
