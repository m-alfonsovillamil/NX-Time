package com.nxtime.nxtime.dto.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Una contraseña nueva que no esté entre las más usadas (plan J, fase J8;
 * ADR 051).
 *
 * Es lo que pide el NIST (SP 800-63B, apartado 3.1.1.2) en lugar de obligar a
 * mezclar mayúsculas, números y símbolos: «Contraseña1!» cumple cualquier regla
 * de composición y está en todas las listas que se prueban primero. Con esta
 * lista de bloqueo el mínimo puede quedarse en 8, que es lo que dice la misma
 * norma.
 *
 * La lista es local ({@code seguridad/contrasenas-comunes.txt}, unas diez mil):
 * no se llama a ningún servicio de fuera, porque mandar la contraseña de
 * alguien a un tercero --ni siquiera un trozo de su hash-- es justo lo que no
 * hay que hacer con ella.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoEsComun.Validador.class)
public @interface NoEsComun {

    String message() default "Esa contraseña es de las más usadas, y es lo primero que se prueba para entrar "
            + "en una cuenta ajena. Elige otra: mejor una frase de varias palabras que te sea fácil de recordar.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<NoEsComun, CharSequence> {

        @Override
        public boolean isValid(CharSequence valor, ConstraintValidatorContext contexto) {
            // Vacía o ausente lo dicen @NotBlank y @Size.
            return valor == null || !Lista.es(valor.toString());
        }
    }

    /** La lista, leída una vez y la primera vez que hace falta. */
    final class Lista {

        static final String RECURSO = "/seguridad/contrasenas-comunes.txt";

        private static final Set<String> COMUNES = leer();

        private Lista() {
        }

        public static boolean es(String contrasena) {
            return COMUNES.contains(contrasena.toLowerCase(Locale.ROOT));
        }

        static int cuantas() {
            return COMUNES.size();
        }

        private static Set<String> leer() {
            try (InputStream entrada = NoEsComun.class.getResourceAsStream(RECURSO)) {
                if (entrada == null) {
                    // Sin la lista, que no arranque en silencio sin protección.
                    throw new IllegalStateException("Falta " + RECURSO + " en el classpath.");
                }
                BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
                return lector.lines()
                        .filter(linea -> !linea.isEmpty() && !linea.startsWith("#"))
                        .map(linea -> linea.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
