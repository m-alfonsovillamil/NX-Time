package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.ChangePasswordRequest;
import com.nxtime.nxtime.dto.CreateEmployeeRequest;
import com.nxtime.nxtime.dto.CreateManagerRequest;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.dto.ConfirmRegistrationRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import com.nxtime.nxtime.dto.RegistrationPendingResponse;

public interface AuthService {

    /**
     * Registra la empresa y deja a su ADMIN pendiente de confirmar el correo
     * (V37, ADR 034): no abre sesión. Responde lo mismo aunque el correo ya
     * tenga cuenta.
     */
    RegistrationPendingResponse registerManager(RegisterManagerRequest request);

    /** Canjea el código del registro y abre la sesión, como el login. */
    AuthenticationResponse confirmarRegistro(ConfirmRegistrationRequest request);

    AuthenticationResponse login(LoginRequest request);

    /** Cambia el access token expirado por uno nuevo, sin pedir contraseña otra vez. */
    AuthenticationResponse refreshAccessToken(String refreshToken);

    /** Revoca un refresh token concreto (cierra esa sesión, no las demás del usuario). */
    void logout(String refreshToken);

    /**
     * Abre una sesión a quien ya se ha identificado por un camino que no es
     * la contraseña: una cuenta de Google o de Microsoft (ADR 036).
     *
     * Es la misma sesión que la de un login --familia nueva, mismo access
     * token, misma vida según el origen--, y por eso sale de aquí y no de
     * quien hace el SSO: que haya dos sitios emitiendo sesiones es como acaban
     * siendo distintas. <b>No comprueba quién es</b>; eso lo ha hecho ya quien
     * llama. Solo que la cuenta siga de alta.
     */
    AuthenticationResponse abrirSesion(long usuarioId, com.nxtime.nxtime.domain.RefreshToken.Origen origen);

    /**
     * De quién es este refresh token, si sigue vivo. <b>No lo rota ni lo
     * toca</b>: sirve para saber quién tiene abierta la sesión de la web cuando
     * llega una navegación del navegador, que trae la cookie pero no el access
     * token (vincular una cuenta de fuera, ADR 036).
     */
    java.util.Optional<Long> usuarioDelRefresh(String refreshToken);

    /**
     * Revoca <b>todos</b> los refresh tokens del usuario: cierra la sesión
     * en todos los dispositivos, incluido aquel desde el que se pide.
     *
     * El access token que ya esté emitido sigue valiendo hasta que caduque
     * (15 min): es un JWT y no se consulta en base, así que "cerrar" aquí
     * significa que nadie podrá renovarlo. Quien lo pida desde su móvil
     * seguirá dentro un rato y después irá al login como los demás; la
     * pantalla lo dice, porque prometer un corte inmediato sería mentir.
     */
    void cerrarTodasLasSesiones(User user);

    void createEmployee(CreateEmployeeRequest request, User manager);

    void createManager(CreateManagerRequest request, User admin);

    /**
     * Cambia la contraseña y cierra las demás sesiones; la de {@code sesionActual}
     * sigue abierta. Sin ella (un token de antes del ADR 034), se cierran todas.
     */
    void changePassword(ChangePasswordRequest request, User user, java.util.Optional<java.util.UUID> sesionActual);

    /** Da de alta o de baja a un empleado de la misma empresa que quien gestiona. */
    void setEmployeeActive(long employeeId, boolean activo, User actingManager);
}
