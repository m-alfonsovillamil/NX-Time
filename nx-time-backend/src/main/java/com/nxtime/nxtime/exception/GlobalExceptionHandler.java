package com.nxtime.nxtime.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejo de errores centralizado (RFC 7807 / ProblemDetail).
 *
 * Antes de esto, NINGÚN error de la API llegaba al cliente con su
 * código real: tanto las excepciones no controladas como las
 * ResponseStatusException correctamente lanzadas acababan en 403,
 * porque usaban response.sendError(), que dispara un dispatch interno a
 * "/error" y ese dispatch vuelve a pasar por el filtro de seguridad y
 * cae en el anyRequest().denyAll() (ver el commit de la Fase 0 y los
 * tests de contrato marcados "bugActual").
 *
 * Los métodos @ExceptionHandler de aquí NO usan sendError(): construyen
 * la respuesta directamente a través de los HttpMessageConverter de
 * Spring MVC, dentro del propio ciclo de DispatcherServlet -- por eso
 * evitan ese bug de raíz, sin tocar la configuración de seguridad.
 *
 * Las excepciones "estándar" de Spring MVC (validación con @Valid,
 * JSON malformado, método HTTP no soportado...) las resuelve Spring
 * Boot automáticamente en ProblemDetail gracias a
 * spring.mvc.problemdetails.enabled=true (ver application.yml); no
 * hace falta declararlas aquí.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        log.warn("Regla de negocio incumplida ({}): {}", ex.getStatus(), ex.getMessage());
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(TenantAccessException.class)
    public ProblemDetail handleTenantAccess(TenantAccessException ex) {
        log.warn("Acceso entre empresas bloqueado: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta acción.");
    }

    /**
     * Validación de parámetros sueltos (@RequestParam, @PathVariable) en
     * clases anotadas con @Validated -- por ejemplo el mes y el año de
     * los informes (Fase 10).
     *
     * Hace falta declararla a mano: spring.mvc.problemdetails.enabled
     * cubre MethodArgumentNotValidException (la de @Valid @RequestBody)
     * pero NO ConstraintViolationException, que es la que se lanza aquí.
     * Sin este manejador, pedir el mes 13 devolvía un 500 genérico en
     * vez de decir que el parámetro está mal.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        String detalle = ex.getConstraintViolations().stream()
                .map(violacion -> {
                    // El propertyPath incluye el nombre del método
                    // ("exportarHorasEnExcel.mes"); al cliente solo le
                    // interesa el parámetro.
                    String ruta = violacion.getPropertyPath().toString();
                    String parametro = ruta.contains(".") ? ruta.substring(ruta.lastIndexOf('.') + 1) : ruta;
                    return parametro + ": " + violacion.getMessage();
                })
                .collect(Collectors.joining("; "));

        log.warn("Parámetros inválidos: {}", detalle);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.warn("Fallo de autenticación: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales incorrectas.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Error inesperado no controlado", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado.");
    }
}
