package com.nxtime.nxtime.exception;

import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lo que responde un cuerpo que no pasa la validación ({@code @Valid}): un 400
 * con los mensajes de los campos en {@code detail}, que es lo que los clientes
 * enseñan (ver {@code util/errores.ts} y {@code ApiErrorParser.kt}).
 *
 * Hasta octubre de 2026 lo resolvía Spring Boot ({@code
 * spring.mvc.problemdetails.enabled}), que pone siempre «Invalid request
 * content.» y se guarda los mensajes. Los formularios lo comprobaban casi todo
 * antes de mandar, así que apenas se notaba; con la lista de contraseñas
 * comunes (ADR 051), que solo puede comprobar el servidor, la persona se
 * quedaba sin saber qué tenía que cambiar.
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

    private static String nombre(org.springframework.validation.ObjectError error) {
        return error instanceof FieldError campo ? campo.getField() : "";
    }
}
