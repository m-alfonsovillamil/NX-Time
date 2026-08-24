package com.nxtime.nxtime.exception;

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
