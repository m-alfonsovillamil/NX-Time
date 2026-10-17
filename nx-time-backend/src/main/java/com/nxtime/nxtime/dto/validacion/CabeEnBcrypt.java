package com.nxtime.nxtime.dto.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Una contraseña nueva que BCrypt puede guardar entera: como mucho 72 BYTES en
 * UTF-8 (revisión de seguridad del 17/10/2026, plan J).
 *
 * El {@code @Size(max = 72)} de siempre cuenta caracteres, y BCrypt cuenta
 * bytes: «ñ» o «é» ocupan dos, así que una contraseña de 72 caracteres podía
 * tener hasta 288 bytes. Lo que pasa de 72 BCrypt no lo mira (o, según la
 * versión de Spring Security, lo rechaza con un error), y dos contraseñas que
 * solo se distinguen al final serían la misma. Va junto al {@code @Size}, que
 * sigue diciendo el mínimo.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CabeEnBcrypt.Validador.class)
public @interface CabeEnBcrypt {

    int MAXIMO_BYTES = 72;

    String message() default "La contraseña es demasiado larga: como mucho 72 letras sin tilde (las letras con tilde y la ñ cuentan doble).";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<CabeEnBcrypt, CharSequence> {
        @Override
        public boolean isValid(CharSequence valor, ConstraintValidatorContext contexto) {
            // Vacía o ausente lo dicen @NotBlank y @Size.
            return valor == null || valor.toString().getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES;
        }
    }
}
