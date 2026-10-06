package com.nxtime.nxtime.dto;

import java.time.Instant;

/**
 * Una cuenta de fuera con la que entro, para «Cuentas vinculadas».
 *
 * @param proveedor     {@code google} o {@code microsoft}
 * @param nombre        cómo se llama de cara a la gente («Google»)
 * @param correo        el de esa cuenta cuando se vinculó
 * @param ultimoAcceso  la última vez que se entró con ella, o null
 */
public record LinkedIdentityDTO(String proveedor, String nombre, String correo, Instant vinculadaEn, Instant ultimoAcceso) {
}
