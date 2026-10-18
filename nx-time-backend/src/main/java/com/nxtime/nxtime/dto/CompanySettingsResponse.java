package com.nxtime.nxtime.dto;

/**
 * Los ajustes de la empresa (fase Z2).
 *
 * @param zonaHoraria nombre IANA de la zona en la que se cuentan sus días
 *   (ADR 032)
 * @param contactoPrivacidad correo o enlace https para ejercer los derechos
 *   del RGPD (ADR 047); null si no lo hay
 * @param firmasInvalidadas cuántas firmas mensuales cayeron por el cambio de
 *   zona que acaba de guardarse; 0 al leer y si la zona no cambió
 */
public record CompanySettingsResponse(String nombre, String zonaHoraria, String contactoPrivacidad,
        int firmasInvalidadas) {
}
