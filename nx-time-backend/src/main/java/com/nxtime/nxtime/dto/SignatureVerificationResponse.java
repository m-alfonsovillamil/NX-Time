package com.nxtime.nxtime.dto;

import com.nxtime.nxtime.domain.MonthlySignatureStatus;

/**
 * Lo firmado contra lo que hay hoy (Fase B3).
 *
 * @param coincide si el mes de hoy da la misma huella que la que se firmó.
 *   Cubre cualquier camino que cambie un fichaje sin pasar por la auditoría:
 *   una firma VIGENTE que no coincide es un dato tocado por fuera.
 */
public record SignatureVerificationResponse(
        long firmaId,
        MonthlySignatureStatus estado,
        boolean coincide,
        String hashFirmado,
        String hashActual
) {
}
