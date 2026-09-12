package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.ChangePasswordRequest;
import com.nxtime.nxtime.dto.CreateEmployeeRequest;
import com.nxtime.nxtime.dto.CreateManagerRequest;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;

public interface AuthService {

    AuthenticationResponse registerManager(RegisterManagerRequest request);

    AuthenticationResponse login(LoginRequest request);

    /** Cambia el access token expirado por uno nuevo, sin pedir contraseña otra vez. */
    AuthenticationResponse refreshAccessToken(String refreshToken);

    /** Revoca un refresh token concreto (cierra esa sesión, no las demás del usuario). */
    void logout(String refreshToken);

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

    void changePassword(ChangePasswordRequest request, User user);

    /** Da de alta o de baja a un empleado de la misma empresa que quien gestiona. */
    void setEmployeeActive(long employeeId, boolean activo, User actingManager);
}
