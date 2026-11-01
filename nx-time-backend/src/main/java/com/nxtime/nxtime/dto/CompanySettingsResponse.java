package com.nxtime.nxtime.dto;

/**
 * Los ajustes de la empresa (fase Z2).
 *
 * @param zonaHoraria nombre IANA de la zona en la que se cuentan sus días
 *   (ADR 032)
 * @param contactoPrivacidad correo o enlace https para ejercer los derechos
 *   del RGPD (ADR 047); null si no lo hay
 * @param exigirSegundoFactor si sus ADMIN y RRHH tienen que entrar con
 *   segundo factor (ADR 051)
 * @param firmasInvalidadas cuántas firmas mensuales cayeron por el cambio de
 *   zona que acaba de guardarse; 0 al leer y si la zona no cambió
 * @param preguntarModalidad si al entrar se pregunta presencial o a distancia (ADR 055)
 */
public record CompanySettingsResponse(String nombre, String zonaHoraria, String contactoPrivacidad,
        boolean exigirSegundoFactor, int firmasInvalidadas, boolean preguntarModalidad) {
}
