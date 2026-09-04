package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.ProfileResponse;
import com.nxtime.nxtime.dto.SimpleEmployeeDTO;
import com.nxtime.nxtime.dto.UpdateEmployeeProfileRequest;
import com.nxtime.nxtime.dto.UpdateProfileRequest;
import java.util.List;

/**
 * La ficha de un empleado: los datos que alguien de RRHH decide sobre
 * él, no los que se derivan de fichar (Fase A).
 *
 * Vive fuera de {@link AuthService} a propósito. Ahí acabó la
 * administración de empleados por herencia -- se llama "Auth" y ya hace
 * altas, bajas y contraseñas -- y añadirle el repositorio de vacaciones
 * habría sido su séptima dependencia de constructor. Aquí, además, es
 * donde crecerá el perfil completo (foto, CV, puesto, departamento).
 */
public interface EmployeeProfileService {

    /**
     * Los empleados de la empresa de quien pregunta, con su jornada y
     * sus días de vacaciones efectivos del año en curso.
     */
    List<SimpleEmployeeDTO> getMyEmployees(User manager);

    /**
     * Configura jornada semanal y/o días de vacaciones del AÑO EN CURSO
     * (Europe/Madrid). Los campos null no se tocan.
     *
     * @throws com.nxtime.nxtime.exception.ResourceNotFoundException si el empleado no existe
     * @throws com.nxtime.nxtime.exception.TenantAccessException si es de otra empresa
     */
    SimpleEmployeeDTO updateProfile(long employeeId, UpdateEmployeeProfileRequest request, User actor);

    /** Mi propia ficha completa. */
    ProfileResponse getMyProfile(User actor);

    /**
     * Cambia MIS datos personales. Ni rol, ni jornada, ni vacaciones, ni
     * departamento: eso no es un dato personal (ver
     * {@link UpdateProfileRequest}). Null = no tocar.
     */
    ProfileResponse updateMyProfile(UpdateProfileRequest request, User actor);

    /**
     * La ficha de otra persona de mi empresa, para quien tenga
     * {@code empleado:leer}.
     *
     * @throws com.nxtime.nxtime.exception.TenantAccessException si es de otra empresa
     */
    ProfileResponse getProfile(long usuarioId, User actor);

    /** Asigna (o quita, con null) el departamento de un empleado. */
    ProfileResponse assignDepartment(long usuarioId, Long departamentoId, User actor);
}
