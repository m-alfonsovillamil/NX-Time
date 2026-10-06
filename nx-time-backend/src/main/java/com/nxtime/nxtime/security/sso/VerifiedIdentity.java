package com.nxtime.nxtime.security.sso;

import com.nxtime.nxtime.domain.SsoProvider;

/**
 * Lo que el proveedor dice de quien acaba de entrar, ya con el ID token validado.
 *
 * @param sujeto           el {@code sub}: el identificador estable de la cuenta en el proveedor
 * @param correo           en minúsculas, o null si el proveedor no lo da
 * @param correoVerificado si el proveedor <b>garantiza</b> que ese correo es de esta persona.
 *                         Es lo único que autoriza a buscar la cuenta de NX Time por el correo
 */
public record VerifiedIdentity(SsoProvider proveedor, String sujeto, String correo, boolean correoVerificado) {
}
