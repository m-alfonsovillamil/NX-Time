package com.nxtime.nxtime.exception;

import com.nxtime.nxtime.web.TextoSinCaracterNulo;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Lo que responde una petición mal formada: un 400 con un {@code detail} en
 * español que diga qué falla, que es lo que los clientes enseñan (ver
 * {@code util/errores.ts} y {@code ApiErrorParser.kt}).
 *
 * Hasta octubre de 2026 todo esto lo resolvía Spring Boot ({@code
 * spring.mvc.problemdetails.enabled}), que contesta en inglés y sin decir qué
 * campo es: «Invalid request content.», «Failed to read request», «Required
 * parameter 'fecha' is not present.». Los formularios lo comprobaban casi todo
 * antes de mandar, así que apenas se notaba; con la lista de contraseñas
 * comunes (ADR 051), que solo puede comprobar el servidor, la persona se
 * quedaba sin saber qué tenía que cambiar. Lo demás (un valor que no existe en
 * una lista, una fecha imposible, un parámetro que falta) se pasó aquí en la
 * revisión del 15/11/2026.
 *
 * Aparte de {@link GlobalExceptionHandler} y por delante del de Spring Boot,
 * que va con orden 0: entre dos {@code @ControllerAdvice} que saben resolver
 * la misma excepción gana el de menor orden.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErroresDeValidacion {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getAllErrors().stream()
                // Los mensajes de los campos primero, y en un orden fijo: el
                // de BindingResult depende del orden de los validadores.
                .sorted((a, b) -> nombre(a).compareTo(nombre(b)))
                .map(error -> error.getDefaultMessage() == null ? "Hay un dato que no es válido." : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                detalle.isBlank() ? "Hay un dato que no es válido." : detalle);
    }

    /**
     * El cuerpo no se ha podido leer: falta, no es JSON, o un campo trae algo
     * que no encaja con su tipo (un estado que no existe, una fecha imposible,
     * un número donde va un texto, un carácter nulo).
     *
     * Si Jackson sabe en qué campo ha sido, se dice cuál. El nombre es el del
     * contrato ({@code fechaInicio}), no una etiqueta de pantalla: quien lo ve
     * es casi siempre quien programa un cliente, porque los formularios no
     * dejan mandar nada de esto.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex) {
        JacksonException jackson = causaDeJackson(ex);
        String campo = jackson == null ? "" : campo(jackson);
        String detalle;
        if (jackson instanceof InvalidFormatException formato && TextoSinCaracterNulo.esSuya(formato)) {
            detalle = campo.isEmpty()
                    ? "Un texto lleva un carácter que no se puede guardar."
                    : "El dato «" + campo + "» lleva un carácter que no se puede guardar.";
        } else if (!campo.isEmpty()) {
            detalle = "El dato «" + campo + "» no es válido.";
        } else {
            detalle = "Falta el cuerpo de la petición o no es un JSON válido.";
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Falta el parámetro «" + ex.getParameterName() + "».");
    }

    /** Un id que no es un número, una fecha que no es una fecha, un valor fuera de su lista. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El parámetro «" + ex.getName() + "» no es válido.");
    }

    /**
     * Los tres que solo ve quien programa un cliente: ningún formulario los
     * provoca. Se contestan igual que los contestaba Spring Boot (el 405 con
     * su cabecera {@code Allow}), pero en español, como todo lo demás.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        HttpHeaders cabeceras = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            cabeceras.setAllow(ex.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(cabeceras)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED,
                        "Esta ruta no admite " + ex.getMethod() + "."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Esta ruta no admite el formato " + ex.getContentType() + ".");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Esa ruta no existe.");
    }

    private static JacksonException causaDeJackson(Throwable ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof JacksonException jackson) {
                return jackson;
            }
        }
        return null;
    }

    /** La ruta del campo: {@code fichajes[0].ubicacion.latitud}. */
    private static String campo(JacksonException ex) {
        StringBuilder ruta = new StringBuilder();
        for (JacksonException.Reference paso : ex.getPath()) {
            if (paso.getPropertyName() != null) {
                if (!ruta.isEmpty()) {
                    ruta.append('.');
                }
                ruta.append(paso.getPropertyName());
            } else if (paso.getIndex() >= 0) {
                ruta.append('[').append(paso.getIndex()).append(']');
            }
        }
        return ruta.toString();
    }

    private static String nombre(org.springframework.validation.ObjectError error) {
        return error instanceof FieldError campo ? campo.getField() : "";
    }
}
