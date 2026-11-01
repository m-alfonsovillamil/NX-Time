package com.nxtime.nxtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que se puede cambiar de la empresa (fase Z2). Se guarda entero: es un
 * formulario pequeño.
 *
 * @param zonaHoraria nombre IANA ({@code Europe/Madrid}, {@code Atlantic/Canary}...).
 *   Lo valida el servicio contra las zonas que conoce Java.
 * @param contactoPrivacidad a quién se dirige la plantilla para ejercer sus
 *   derechos del RGPD: un correo o un enlace https (V44, ADR 047). Vacío o
 *   null lo quita.
 * @param exigirSegundoFactor exigir el segundo factor a los ADMIN y RRHH
 *   (ADR 051). Null lo deja como está, para que un cliente que no lo conozca
 *   no lo apague sin querer.
 */
public record UpdateCompanySettingsRequest(
        @NotBlank(message = "El nombre de la empresa es obligatorio.")
        @Size(max = 255, message = "El nombre no puede pasar de 255 caracteres.")
        String nombre,

        @NotBlank(message = "La zona horaria es obligatoria.")
        @Size(max = 64, message = "La zona horaria no puede pasar de 64 caracteres.")
        String zonaHoraria,

        @Size(max = 255, message = "El contacto de privacidad no puede pasar de 255 caracteres.")
        String contactoPrivacidad,

        Boolean exigirSegundoFactor,

        /** Preguntar presencial o a distancia al entrar (ADR 055). Null lo deja como está. */
        Boolean preguntarModalidad) {

    /** Lo de antes del ADR 051: el segundo factor, como esté. */
    public UpdateCompanySettingsRequest(String nombre, String zonaHoraria, String contactoPrivacidad) {
        this(nombre, zonaHoraria, contactoPrivacidad, null, null);
    }

    /** Lo de antes del ADR 055: la modalidad, como esté. */
    public UpdateCompanySettingsRequest(String nombre, String zonaHoraria, String contactoPrivacidad,
            Boolean exigirSegundoFactor) {
        this(nombre, zonaHoraria, contactoPrivacidad, exigirSegundoFactor, null);
    }
}
