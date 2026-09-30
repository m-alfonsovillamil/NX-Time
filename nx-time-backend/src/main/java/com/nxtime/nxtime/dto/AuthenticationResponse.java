package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.Role;
import java.util.List;

/**
 * DTO que el backend devuelve tras un login o registro exitoso.
 *
 * refreshToken desde la Fase 4: el access token (campo "token") dura
 * poco (15 min); refreshToken es de larga duración y sirve para pedir
 * uno nuevo por /auth/refresh sin volver a pedir contraseña -- ver
 * RefreshToken.
 *
 * @param authorities lo que esta persona puede hacer, resuelto por el
 *   servidor a partir de su rol ({@code RoleAuthorities.enOrden}). Viaja
 *   ya en el login, y no solo en {@code GET /api/v1/perfil}, porque el
 *   cliente arma su menú con esto antes de tener perfil: si hubiera que
 *   pedirlo aparte habría un hueco, el primero después de entrar, en el
 *   que la aplicación no sabría qué ofrecer. Viaja también en el refresco
 *   para que un cambio de rol llegue sin necesidad de volver a entrar.
 *
 *   <b>No autoriza nada</b> -- eso lo sigue haciendo el
 *   {@code @PreAuthorize} de cada endpoint --, solo decide qué se enseña.
 *   El campo {@code rol} se queda porque hay sitios donde lo que se
 *   quiere es nombrarlo ("Gestor"), no comprobar un permiso.
 *
 * @param zonaHoraria la zona de su empresa, en nombre IANA (ADR 032). Con ella
 *   el cliente decide qué día es "hoy" y en qué hora enseña los fichajes, en
 *   vez de suponer Madrid. Viaja en el refresco por lo mismo que las
 *   authorities: un cambio de zona llega sin volver a entrar.
 */
public record AuthenticationResponse(
        String token, String refreshToken, String nombre, Role rol, List<String> authorities,
        String zonaHoraria) {

    /**
     * La misma respuesta sin el refresh, para el navegador (ADR 030): allí el
     * refresh viaja en una cookie {@code HttpOnly}, y ponerlo también en el
     * cuerpo se lo daría al JavaScript de la página, que es justo de quien se
     * quiere esconder.
     */
    public AuthenticationResponse sinRefreshToken() {
        return new AuthenticationResponse(token, null, nombre, rol, authorities, zonaHoraria);
    }
}
