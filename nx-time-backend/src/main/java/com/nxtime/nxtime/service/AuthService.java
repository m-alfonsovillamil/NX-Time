package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.AuthenticationResponse;
import com.nxtime.nxtime.dto.ChangePasswordRequest;
import com.nxtime.nxtime.dto.CreateEmployeeRequest;
import com.nxtime.nxtime.dto.CreateManagerRequest;
import com.nxtime.nxtime.dto.LoginRequest;
import com.nxtime.nxtime.dto.RegisterManagerRequest;
import com.nxtime.nxtime.dto.SimpleEmployeeDTO;
import java.util.List;

public interface AuthService {

    AuthenticationResponse registerManager(RegisterManagerRequest request);

    AuthenticationResponse login(LoginRequest request);

    /** Cambia el access token expirado por uno nuevo, sin pedir contraseña otra vez. */
    AuthenticationResponse refreshAccessToken(String refreshToken);

    /** Revoca un refresh token concreto (cierra esa sesión, no las demás del usuario). */
    void logout(String refreshToken);

    void createEmployee(CreateEmployeeRequest request, User manager);

    void createManager(CreateManagerRequest request, User admin);

    void changePassword(ChangePasswordRequest request, User user);

    List<SimpleEmployeeDTO> getMyEmployees(User manager);

    /** Da de alta o de baja a un empleado de la misma empresa que quien gestiona. */
    void setEmployeeActive(long employeeId, boolean activo, User actingManager);
}
