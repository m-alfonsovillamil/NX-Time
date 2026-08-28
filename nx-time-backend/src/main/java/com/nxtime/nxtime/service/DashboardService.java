package com.nxtime.nxtime.service;

import com.nxtime.nxtime.dto.CompanyDashboardResponse;
import com.nxtime.nxtime.dto.PersonalDashboardResponse;

public interface DashboardService {

    /** Resumen del propio empleado: horas de hoy/semana/mes, estado actual y pendientes. */
    PersonalDashboardResponse getPersonalDashboard(String email);

    /** Resumen de la empresa del gestor: agregados del mes y trabajo pendiente. */
    CompanyDashboardResponse getCompanyDashboard(String managerEmail);
}
