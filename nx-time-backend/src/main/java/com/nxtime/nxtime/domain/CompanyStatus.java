package com.nxtime.nxtime.domain;

/**
 * En qué punto está una empresa en el servicio (V43, ADR 046).
 *
 * Solo en {@link #ACTIVA} puede entrar alguien de ella. Los nombres son los de
 * la columna {@code empresas.estado}.
 */
public enum CompanyStatus {

    /** Registrada y con el correo de su ADMIN confirmado, esperando a que el operador la apruebe. */
    PENDIENTE,

    ACTIVA,

    /** Parada por el operador. Sus datos siguen ahí; nadie de ella entra hasta que se reactive. */
    SUSPENDIDA
}
