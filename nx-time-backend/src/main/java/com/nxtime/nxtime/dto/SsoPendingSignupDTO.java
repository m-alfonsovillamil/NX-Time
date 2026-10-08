package com.nxtime.nxtime.dto;

/**
 * Con qué cuenta se está registrando una empresa (ADR 038), para que la web
 * lo diga encima del formulario.
 *
 * @param proveedor el id ({@code google}, {@code microsoft})
 * @param nombre    cómo se llama el proveedor
 * @param correo    el de esa cuenta: será el de la cuenta de NX Time
 */
public record SsoPendingSignupDTO(String proveedor, String nombre, String correo) {
}
