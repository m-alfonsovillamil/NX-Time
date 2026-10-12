package com.nxtime.nxtime.dto;

/**
 * Lo que recibe la app al cambiar su código de registro (ADR 044): con qué
 * cuenta está registrando la empresa, para decirlo encima del formulario, y el
 * pase con el que mandarlo.
 *
 * @param proveedor el id ({@code google}, {@code microsoft})
 * @param nombre    cómo se llama el proveedor
 * @param correo    el de esa cuenta: será el de la cuenta de NX Time
 * @param pase      lo que la web lleva en la cookie {@code nx_sso_alta}: vale un cuarto de hora
 */
public record SsoAppPendingSignupDTO(String proveedor, String nombre, String correo, String pase) {
}
