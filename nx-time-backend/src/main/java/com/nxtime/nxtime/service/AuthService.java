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

    void createEmployee(CreateEmployeeRequest request, User manager);

    void createManager(CreateManagerRequest request, User admin);

    void changePassword(ChangePasswordRequest request, User user);

    List<SimpleEmployeeDTO> getMyEmployees(User manager);
}
