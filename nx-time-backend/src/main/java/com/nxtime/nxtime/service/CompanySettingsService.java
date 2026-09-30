package com.nxtime.nxtime.service;

import com.nxtime.nxtime.domain.User;
import com.nxtime.nxtime.dto.CompanySettingsResponse;
import com.nxtime.nxtime.dto.UpdateCompanySettingsRequest;

/** Los ajustes de la empresa de quien pregunta (fase Z2). */
public interface CompanySettingsService {

    CompanySettingsResponse leer(User actor);

    /**
     * Guarda nombre y zona. Si la zona cambia, revisa las firmas mensuales
     * vigentes (ADR 032): puede que alguna caiga.
     *
     * @throws com.nxtime.nxtime.exception.BusinessException 400 si la zona no
     *     existe, 409 si otra empresa ya se llama así
     */
    CompanySettingsResponse guardar(UpdateCompanySettingsRequest request, User actor);
}
