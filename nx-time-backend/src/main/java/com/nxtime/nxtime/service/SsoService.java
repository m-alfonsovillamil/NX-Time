package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.SsoProvider;
import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.LinkedIdentityDTO;
import com.nxtime.nxtime.security.sso.VerifiedIdentity;
import java.util.List;

/**
 * De una cuenta de Google o de Microsoft a una persona de NX Time (ADR 036).
 *
 * Aquí está la regla, no el protocolo: quién entra y quién no, una vez que el
 * proveedor ya ha dicho quién es (eso es {@code OidcClient}).
 */
public interface SsoService {

    /**
     * La persona de NX Time a la que corresponde esa identidad, lista para
     * abrirle la sesión. Si es la primera vez y el correo está garantizado, la
     * deja vinculada. Lanza {@code SsoException} con el motivo si no entra.
     */
    User identificar(VerifiedIdentity identidad);

    /** Añade esa cuenta a esta persona, que ya tiene la sesión abierta. */
    void vincular(long usuarioId, VerifiedIdentity identidad);

    List<LinkedIdentityDTO> identidadesDe(User usuario);

    void desvincular(User usuario, SsoProvider proveedor);
}
