package com.nxtime.nxtime.dto;

/**
 * Lo que necesita el aviso de privacidad de la primera entrada (ADR 047). El
 * texto lo pone cada cliente; esto son solo los datos de la empresa.
 *
 * @param empresa la responsable del tratamiento de los datos de su plantilla
 * @param contacto correo o enlace https al que dirigirse para ejercer los
 *     derechos; null si el ADMIN no lo ha puesto (entonces, «tu empresa»)
 * @param visto si esta persona ya lo ha visto: entonces no hay que enseñarlo
 */
public record PrivacyNoticeResponse(String empresa, String contacto, boolean visto) {
}
