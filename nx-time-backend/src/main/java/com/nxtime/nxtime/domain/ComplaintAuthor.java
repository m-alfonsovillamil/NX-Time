package com.nxtime.nxtime.domain;

/**
 * Quién escribe un mensaje del expediente (Fase G).
 *
 * Existe porque {@code autor_id} no puede decirlo: en una denuncia
 * anónima el mensaje del denunciante va sin autor, y ahí un NULL sería
 * indistinguible de un dato que falta. Con esto, la pantalla sabe de
 * qué lado pintar el mensaje sin preguntarle nada al anonimato.
 */
public enum ComplaintAuthor {

    /** Quien presentó la denuncia, se identificara o no. */
    DENUNCIANTE,

    /** Quien la instruye: el Responsable del Sistema Interno de Información. */
    INSTRUCTOR
}
