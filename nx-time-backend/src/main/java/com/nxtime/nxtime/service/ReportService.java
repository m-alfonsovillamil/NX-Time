package com.nxtime.nxtime.service;

import com.nxtime.nxtime.report.MonthlyReport;
import java.time.YearMonth;

public interface ReportService {

    /** Informe de toda la empresa del solicitante para ese mes (base del Excel). */
    MonthlyReport informeDeEmpresa(String solicitanteEmail, YearMonth mes);

    /** Informe individual de un empleado (base del PDF legal mensual). */
    MonthlyReport informeDeEmpleado(String solicitanteEmail, long empleadoId, YearMonth mes);
}
