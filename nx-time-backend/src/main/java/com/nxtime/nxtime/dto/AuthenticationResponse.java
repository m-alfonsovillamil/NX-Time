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
 */
public record AuthenticationResponse(
        String token, String refreshToken, String nombre, Role rol, List<String> authorities) {
}
